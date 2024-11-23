package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType
import com.quran.labs.androidquran.extra.feature.linebyline.model.LineModel
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.ui.modifier.pageGradient
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import com.quran.mobile.linebyline.ui.renderer.composable.QuranLineLayout
import com.quran.mobile.linebyline.ui.renderer.composable.QuranPageLayout
import kotlinx.coroutines.launch

@Composable
fun QuranPage(
  pageInfo: PageInfo,
  pageContentType: PageContentType,
  isScrollable: Boolean,
  headerComposable: @Composable () -> Unit,
  footerComposable: @Composable () -> Unit,
  quranLineComposable: @Composable (LineModel) -> Unit,
  suraHeaderComposable: @Composable (SuraHeader) -> Unit,
  ayahMarkerInfoComposable: @Composable (AyahMarkerInfo) -> Unit,
  highlightComposable: @Composable (AyahHighlight, Color) -> Unit,
  sidelinesComposable: @Composable () -> Unit,
  onPagePositioned: (Float, Float, Int, Int) -> Unit,
  onSelectionStart: (Float, Float) -> Unit,
  onSelectionModified: (Float, Float) -> Unit,
  onSelectionEnd: () -> Unit,
  modifier: Modifier = Modifier,
  isNightMode: Boolean = false,
  nightModeBackgroundBrightness: Int = 0
) {
  val mod = if (isNightMode) {
    modifier.background(
      Color(
        nightModeBackgroundBrightness,
        nightModeBackgroundBrightness,
        nightModeBackgroundBrightness
      ))
  } else {
    val skip = pageInfo.skippedPageCount
    val target = skip % 2
    modifier.pageGradient(pageInfo.page % 2 == target)
  }

  val scrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(key1 = pageInfo.targetScrollPosition) {
    if (pageInfo.targetScrollPosition >= 0 && pageInfo.targetScrollPosition != scrollState.value) {
      coroutineScope.launch {
        scrollState.animateScrollTo(pageInfo.targetScrollPosition)
      }
    }
  }

  val ratio = (pageContentType as PageContentType.Line).ratio
  val allowLinesToOverlap = pageContentType.allowOverlapOfLines
  Box(modifier = mod.fillMaxSize()) {
    QuranPageLayout(
      pageInfo.page,
      isScrollable = isScrollable,
      scrollState = scrollState,
      showSidelines = pageInfo.showSidelines,
      header = headerComposable,
      footer = footerComposable,
      sidelines = sidelinesComposable
    ) {
      Box {
        QuranLineLayout(
          lineHeightWidthRatio = ratio,
          allowLinesToOverlap = allowLinesToOverlap,
          modifier = Modifier
            .pointerInput(Unit) {
              detectDragGesturesAfterLongPress(
                onDragStart = { onSelectionStart(it.x, it.y) },
                onDragEnd = { onSelectionEnd() },
              ) { change, dragAmount ->
                change.consume()
                onSelectionModified(dragAmount.x, dragAmount.y)
              }
            }
            .onGloballyPositioned {
              val position = it.positionInWindow()
              onPagePositioned(position.x, position.y, it.size.width, it.size.height)
            }
        ) {
          pageInfo.lineModels.forEach { lineInfo ->
            Box {
              val lineId = lineInfo.lineId
              quranLineComposable(lineInfo)

              // Sura Headers
              val suraHeaders = pageInfo.suraHeaders.filter { it.lineId == lineId }
              suraHeaders.forEach {
                suraHeaderComposable(it)
              }
            }
          }
        }

        // highlights and ayah markers all overlay the QuranLineLayout
        pageInfo.lineModels.forEach { lineInfo ->
          val lineId = lineInfo.lineId

          // Ayah Markers
          val markers = pageInfo.ayahMarkers.filter { it.lineId == lineId }
          markers.forEach {
            ayahMarkerInfoComposable(it)
          }

          // Highlights
          val highlights = pageInfo.ayahHighlights.filter { highlightAyah ->
            highlightAyah.ayahHighlights.any { it.lineId == lineId }
          }
          highlights.forEach { highlightAyah ->
            val color = when (highlightAyah.highlightType) {
              HighlightType.SELECTION -> Color(0x46, 0x94, 0xa6, 0x40)
              HighlightType.AUDIO -> Color(0x46, 0xa6, 0x46, 0x40)
              HighlightType.BOOKMARK -> Color(0xa4, 0xa4, 0xa4, 0x40)
            }

            highlightAyah.ayahHighlights
              .filter { it.lineId == lineId }
              .forEach {
                highlightComposable(it, color)
              }
          }
        }
      }
    }
  }
}
