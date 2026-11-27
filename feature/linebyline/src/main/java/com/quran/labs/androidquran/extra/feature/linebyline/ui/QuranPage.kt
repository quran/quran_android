package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.extra.feature.linebyline.model.LineModel
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.ui.modifier.pageGradient
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import com.quran.mobile.linebyline.ui.renderer.composable.QuranLineLayout
import com.quran.mobile.linebyline.ui.renderer.composable.QuranPageLayout
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun shouldAnimateScrollTo(
  targetScrollPosition: Int,
  currentScrollValue: Int
): Boolean = targetScrollPosition >= 0 && targetScrollPosition != currentScrollValue

@Composable
fun QuranPage(
  pageInfo: PageInfo,
  pageContentType: PageContentType,
  isScrollable: Boolean,
  headerComposable: @Composable () -> Unit,
  footerComposable: @Composable () -> Unit,
  quranLineComposable: @Composable (LineModel) -> Unit,
  suraHeaderComposable: @Composable (SuraHeader) -> Unit,
  ayahMarkerInfoComposable: @Composable (AyahMarkerInfo, TextMeasurer) -> Unit,
  highlightComposable: @Composable (AyahHighlight, Color) -> Unit,
  readingBookmarkComposable: @Composable (AyahHighlight, ImmutableList<Color>) -> Unit,
  sidelinesComposable: @Composable () -> Unit,
  onPagePositioned: (Float, Float, Int, Int) -> Unit,
  onSelectionStart: (Float, Float) -> Unit,
  onSelectionModified: (Float, Float) -> Unit,
  onSelectionEnd: () -> Unit,
  modifier: Modifier = Modifier,
  isNightMode: Boolean = false,
  backgroundColor: Color? = null
) {
  val mod = if (backgroundColor != null) {
    modifier.background(backgroundColor)
  } else {
    val skip = pageInfo.skippedPageCount
    val target = skip % 2
    modifier.pageGradient(pageInfo.page % 2 == target)
  }

  val scrollState = rememberScrollState()
  val textMeasurer = rememberTextMeasurer()

  val suraHeadersByLine = remember(pageInfo.suraHeaders) {
    pageInfo.suraHeaders.groupBy { it.lineId }
  }
  val ayahMarkersByLine = remember(pageInfo.ayahMarkers) {
    pageInfo.ayahMarkers.groupBy { it.lineId }
  }
  val underTextHighlights = remember(pageInfo.ayahHighlights, isNightMode) {
    pageInfo.ayahHighlights
      .filter { it.highlightType.isUnderText }
      .map { it.highlightType.paintColor(isNightMode) to it.ayahHighlights }
  }
  val overlayHighlightsByLine = remember(pageInfo.ayahHighlights, isNightMode) {
    buildMap<Int, MutableList<Pair<AyahHighlight, Color>>> {
      pageInfo.ayahHighlights.forEach { highlightAyah ->
        if (!highlightAyah.highlightType.isUnderText) {
          val color = highlightAyah.highlightType.paintColor(isNightMode)
          highlightAyah.ayahHighlights.forEach { ayahHighlight ->
            getOrPut(ayahHighlight.lineId) { mutableListOf() }.add(ayahHighlight to color)
          }
        }
      }
    }
  }
  val readingBookmarkEntries = remember(pageInfo.readingBookmarks, isNightMode) {
    pageInfo.readingBookmarks.map { readingBookmark ->
      val colors = readingBookmark.slots.map { it.paintColor(isNightMode) }.toImmutableList()
      colors to readingBookmark.ayahHighlights
    }
  }

  LaunchedEffect(key1 = pageInfo.targetScrollPosition) {
    if (shouldAnimateScrollTo(pageInfo.targetScrollPosition, scrollState.value)) {
      scrollState.animateScrollTo(pageInfo.targetScrollPosition)
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
        // highlights that act as a background are painted before the lines are drawn
        underTextHighlights.forEach { (color, highlights) ->
          highlights.forEach { highlightComposable(it, color) }
        }

        readingBookmarkEntries.forEach { (colors, ayahHighlights) ->
          ayahHighlights.forEach { readingBookmarkComposable(it, colors) }
        }

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
              suraHeadersByLine[lineId]?.forEach {
                suraHeaderComposable(it)
              }
            }
          }
        }

        // highlights and ayah markers all overlay the QuranLineLayout
        pageInfo.lineModels.forEach { lineInfo ->
          val lineId = lineInfo.lineId

          // Ayah Markers
          ayahMarkersByLine[lineId]?.forEach {
            ayahMarkerInfoComposable(it, textMeasurer)
          }

          // Highlights
          overlayHighlightsByLine[lineId]?.forEach { (highlight, color) ->
            highlightComposable(highlight, color)
          }
        }
      }
    }
  }
}
