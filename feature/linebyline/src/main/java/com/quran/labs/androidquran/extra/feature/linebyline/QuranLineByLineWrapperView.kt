package com.quran.labs.androidquran.extra.feature.linebyline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.FrameLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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

  private fun generateComposeView(): ComposeView {
    val pageFlow = quranLineByLinePresenter.loadPage()
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

    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      consumeWindowInsets = false
      setContent {
        val scope = rememberCoroutineScope()
        val pageInfo = pageFlow.collectAsState(quranLineByLinePresenter.emptyState(), Dispatchers.IO)

        MaterialTheme {
          QuranPageWrapper(
            pageInfo = pageInfo.value,
            pageContentType = pageProvider.getPageContentType(),
            dualScreenMode = dualScreenMode,
            suraHeaderBitmap = imageBitmapUtil.alpha8Image(
              context.applicationContext,
              R.drawable.chapter_hdr
            ),
            ayahNumberFormatter = NumberFormat.getIntegerInstance(resolveAyahNumberLocale(context)),
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

  private fun resolveAyahNumberLocale(context: Context): Locale {
    val appLocale = currentLocale(context)
    return if (appLocale.language == "ar") {
      appLocale
    } else {
      Locale.forLanguageTag("ar-EG")
    }
  }

  private fun currentLocale(context: Context): Locale {
    val configuration = context.resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      configuration.locales[0]
    } else {
      @Suppress("DEPRECATION")
      configuration.locale
    }
  }
}
