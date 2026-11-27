package com.quran.labs.androidquran.extra.feature.linebyline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quran.data.page.provider.di.inject
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.drawing.R
import com.quran.labs.androidquran.extra.feature.linebyline.model.MissingLineByLineImagesException
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.QuranLineByLinePresenter
import com.quran.labs.androidquran.extra.feature.linebyline.resource.ImageBitmapUtil
import com.quran.labs.androidquran.extra.feature.linebyline.ui.QuranPageWrapper
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

class QuranLineByLineWrapperView(
  context: Context,
  currentPage: Int,
  private val dualScreenMode: Boolean = false
) : FrameLayout(context) {
  @Volatile
  private var didRequestFallback = false
  private var wasDetached = false
  private val attachGeneration = mutableIntStateOf(0)

  @Inject
  lateinit var imageBitmapUtil: ImageBitmapUtil
  @Inject
  lateinit var quranLineByLinePresenter: QuranLineByLinePresenter
  @Inject
  lateinit var pageProvider: PageProvider

  init {
    inject(currentPage)
    addView(generateComposeView())
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    quranLineByLinePresenter.attach()
    if (wasDetached) {
      wasDetached = false
      attachGeneration.intValue++
    }
  }

  override fun onDetachedFromWindow() {
    wasDetached = true
    quranLineByLinePresenter.detach()
    super.onDetachedFromWindow()
  }

  private fun generateComposeView(): ComposeView {
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      consumeWindowInsets = false
      setContent {
        val scope = rememberCoroutineScope()
        val generation = attachGeneration.intValue
        val pageFlow = remember(generation, quranLineByLinePresenter) {
          quranLineByLinePresenter.loadPage()
            .catch { throwable ->
              if (throwable is MissingLineByLineImagesException) {
                if (!didRequestFallback) {
                  Timber.e(throwable, "Missing line image")
                  withContext(Dispatchers.Main) {
                    if (quranLineByLinePresenter.fallbackToImageType()) {
                      didRequestFallback = true

                      val activity = resolveActivity(context)
                      finishAndRestart(activity)
                    }
                  }
                }

                emit(quranLineByLinePresenter.emptyState())
              } else {
                throw throwable
              }
            }
        }
        val pageInfo = pageFlow.collectAsStateWithLifecycle(
          initialValue = quranLineByLinePresenter.emptyState(),
          context = Dispatchers.IO
        )
        val suraHeaderBitmap = remember(imageBitmapUtil) {
          imageBitmapUtil.alpha8Image(
            context.applicationContext,
            R.drawable.chapter_hdr
          )
        }
        val ayahNumberFormatter = remember {
          NumberFormat.getIntegerInstance(Locale("ar", "EG"))
        }

        MaterialTheme {
          QuranPageWrapper(
            pageInfo = pageInfo.value,
            pageContentType = pageProvider.getPageContentType(),
            dualScreenMode = dualScreenMode,
            suraHeaderBitmap = suraHeaderBitmap,
            ayahNumberFormatter = ayahNumberFormatter,
            onClick = quranLineByLinePresenter::onClick,
            onPagePositioned = quranLineByLinePresenter::onPagePositioned,
            onSelectionStart = { x, y -> scope.launch { quranLineByLinePresenter.startSelection(x, y) } },
            onSelectionModified = { offsetX, offsetY -> scope.launch { quranLineByLinePresenter.modifySelectionRange(offsetX, offsetY) } },
            onSelectionEnd = { scope.launch { quranLineByLinePresenter.endSelection() } }
          )
        }
      }
    }
  }

  private tailrec fun resolveActivity(context: Context): Activity? {
    return context as? Activity
        ?: if (context is ContextWrapper) {
          resolveActivity(context.baseContext)
        } else {
          null
        }
  }

  private fun finishAndRestart(activity: Activity?) {
    if (activity != null) {
      val restartIntent = Intent(activity.intent)
      activity.finish()
      activity.startActivity(restartIntent)
    }
  }
}
