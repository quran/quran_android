package com.quran.labs.androidquran.extra.feature.linebyline

import android.content.Context
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
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.QuranLineByLinePresenter
import com.quran.labs.androidquran.extra.feature.linebyline.resource.ImageBitmapUtil
import com.quran.labs.androidquran.extra.feature.linebyline.ui.QuranPageWrapper
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class QuranLineByLineWrapperView(
  context: Context,
  currentPage: Int,
  private val dualScreenMode: Boolean = false
) : FrameLayout(context) {

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
            ayahNumberFormatter = NumberFormat.getIntegerInstance(Locale("ar", "EG")),
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
}
