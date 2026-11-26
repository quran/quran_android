package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.ui.modifier.pageGradient
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.ui.renderer.composable.QuranPageLayout
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val BOOKMARK_INSET_RATIO = 1f / 358f
private const val BOOKMARK_BOTTOM_OFFSET_RATIO = 7f / 358f
private const val BOOKMARK_THICKNESS_RATIO = 4.5f / 358f

private class CachedMarkerLayouts(
  var width: Float = -1f,
  var markers: ImmutableList<AyahMarkerInfo>? = null,
  var textColor: Color = Color.Unspecified,
  var formattedLabels: List<String> = emptyList(),
  var layouts: List<TextLayoutResult> = emptyList()
)

internal fun shouldAnimateScrollTo(
  targetScrollPosition: Int,
  currentScrollValue: Int
): Boolean = targetScrollPosition >= 0 && targetScrollPosition != currentScrollValue

@Composable
fun QuranPage(
  pageInfo: PageInfo,
  pageContentType: PageContentType,
  isScrollable: Boolean,
  suraHeaderBitmap: ImageBitmap?,
  ayahNumberFormatter: NumberFormat,
  lineColorFilter: ColorFilter,
  headerColorFilter: ColorFilter,
  lineColor: Color,
  ringColor: Color,
  innerColor: Color,
  markerTextColor: Color,
  shouldShowLineDivider: (Int) -> Boolean,
  headerComposable: @Composable () -> Unit,
  footerComposable: @Composable () -> Unit,
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
  val density = LocalDensity.current

  val suraHeadersByLine = remember(pageInfo.suraHeaders) {
    if (pageInfo.suraHeaders.isEmpty()) emptyMap() else pageInfo.suraHeaders.groupBy { it.lineId }
  }
  val underTextHighlights = remember(pageInfo.ayahHighlights, isNightMode) {
    pageInfo.ayahHighlights
      .filter { it.highlightType.isUnderText }
      .map { it.highlightType.paintColor(isNightMode) to it.ayahHighlights }
  }
  val activeThemeStyle = AppThemeStyle.current
  val overlayHighlights = remember(pageInfo.ayahHighlights, isNightMode, activeThemeStyle) {
    pageInfo.ayahHighlights
      .filter { !it.highlightType.isUnderText }
      .map { it.highlightType.paintColor(isNightMode) to it.ayahHighlights }
  }
  val readingBookmarkEntries = remember(pageInfo.readingBookmarks, isNightMode) {
    pageInfo.readingBookmarks.map { readingBookmark ->
      val colors = readingBookmark.slots.map { it.paintColor(isNightMode) }.toImmutableList()
      colors to readingBookmark.ayahHighlights
    }
  }
  val formattedMarkerNumbers = remember(pageInfo.ayahMarkers, ayahNumberFormatter) {
    pageInfo.ayahMarkers.map { ayahNumberFormatter.format(it.ayah) }
  }
  val baseTextStyle = remember(markerTextColor) { TextStyle.Default.copy(color = markerTextColor) }
  val markerLayoutCache = remember(density) { CachedMarkerLayouts() }
  val pillPath = remember { Path() }

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
      Canvas(
        modifier = Modifier
          .fillMaxSize()
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
        val totalWidth = size.width
        val totalHeight = size.height
        if (totalWidth <= 0f || totalHeight <= 0f || pageInfo.lineModels.isEmpty()) {
          return@Canvas
        }

        val totalWidthInt = totalWidth.toInt()
        val totalHeightInt = totalHeight.toInt()
        val lineHeight = totalWidth * ratio
        val lineHeightInt = lineHeight.toInt()
        val lastLineIndex = 14f
        val lineHeightWithoutOverlap = (totalHeight - lineHeight) / lastLineIndex
        val highlightYStart = (lineHeight - lineHeightWithoutOverlap) / 2f

        // 1. Background (under-text) highlights
        for (i in 0 until underTextHighlights.size) {
          val (color, highlights) = underTextHighlights[i]
          for (j in 0 until highlights.size) {
            drawPageHighlight(
              highlight = highlights[j],
              color = color,
              totalWidth = totalWidth,
              lineHeightWithoutOverlap = lineHeightWithoutOverlap,
              yStart = highlightYStart
            )
          }
        }

        // 2. Reading bookmark underlines
        for (i in 0 until readingBookmarkEntries.size) {
          val (colors, ayahHighlights) = readingBookmarkEntries[i]
          for (j in 0 until ayahHighlights.size) {
            drawReadingBookmarkUnderline(
              highlight = ayahHighlights[j],
              colors = colors,
              totalWidth = totalWidth,
              lineHeightWithoutOverlap = lineHeightWithoutOverlap,
              yStart = highlightYStart,
              pillPath = pillPath
            )
          }
        }

        // 3. 15 Quran lines + optional divider + Sura headers
        val lineCount = pageInfo.lineModels.size
        val layoutLineHeight = if (allowLinesToOverlap) {
          lineHeightInt
        } else {
          totalHeightInt / max(lineCount, 1)
        }
        val layoutLastLineIndex = max(lineCount - 1, 1).toFloat()
        val lineDstSize = IntSize(totalWidthInt, lineHeightInt)

        for (index in 0 until lineCount) {
          val lineInfo = pageInfo.lineModels[index]
          val lineId = lineInfo.lineId
          val lineTopY = floor((totalHeightInt - layoutLineHeight) / layoutLastLineIndex * index).roundToInt()

          drawImage(
            image = lineInfo.lineImage,
            dstOffset = IntOffset(0, lineTopY),
            dstSize = lineDstSize,
            colorFilter = lineColorFilter
          )

          if (shouldShowLineDivider(lineId)) {
            val dividerY = lineTopY.toFloat()
            drawLine(lineColor, start = Offset(0f, dividerY), end = Offset(totalWidth, dividerY))
          }

          val headers = suraHeadersByLine[lineId]
          if (headers != null && suraHeaderBitmap != null) {
            val hdrWidth = (totalWidthInt * 1038) / 1080
            val hdrHeight = (hdrWidth * suraHeaderBitmap.height) / suraHeaderBitmap.width
            val hdrDstSize = IntSize(hdrWidth, hdrHeight)
            for (h in 0 until headers.size) {
              val header = headers[h]
              val xOffset = ((header.centerX * totalWidth) - (hdrWidth / 2)).toInt()
              val yOffset = lineTopY + ((header.centerY * lineHeight) - (hdrHeight / 2)).toInt()
              drawImage(
                image = suraHeaderBitmap,
                dstOffset = IntOffset(xOffset, yOffset),
                dstSize = hdrDstSize,
                colorFilter = headerColorFilter
              )
            }
          }
        }

        // 4. Ayah markers (with cached TextLayoutResults per width/markers/color)
        val markers = pageInfo.ayahMarkers
        if (markers.isNotEmpty()) {
          val layouts = resolveMarkerLayouts(
            cache = markerLayoutCache,
            width = totalWidth,
            markers = markers,
            formattedNumbers = formattedMarkerNumbers,
            textColor = markerTextColor,
            baseTextStyle = baseTextStyle,
            textMeasurer = textMeasurer
          )
          val strokeWidth = 0.003f * totalWidth
          val markerStroke = Stroke(width = strokeWidth)
          val markerDimen = (0.025f * 2 * totalWidth).toInt()
          val radius = markerDimen / 2f
          val markerLineHeight = totalWidth * 174 / 1080
          val yStep = (totalHeight - markerLineHeight) / 14

          for (i in 0 until markers.size) {
            val marker = markers[i]
            val xOffset = (marker.centerX * totalWidth) - (markerDimen / 2)
            val yStart = yStep * marker.lineId
            val yOffset = yStart + (marker.centerY * markerLineHeight) - (markerDimen / 2)
            val center = Offset(xOffset + radius, yOffset + radius)

            drawCircle(innerColor, radius, center = center)
            drawCircle(
              color = ringColor,
              radius = radius,
              center = center,
              style = markerStroke
            )

            val measurement = layouts[i]
            drawText(
              measurement,
              topLeft = Offset(
                xOffset + (radius - (measurement.size.width / 2)),
                yOffset + (radius - (measurement.size.height / 2)) + 4 - 1
              )
            )
          }
        }

        // 5. Overlay highlights (selection / audio)
        for (i in 0 until overlayHighlights.size) {
          val (color, highlights) = overlayHighlights[i]
          for (j in 0 until highlights.size) {
            drawPageHighlight(
              highlight = highlights[j],
              color = color,
              totalWidth = totalWidth,
              lineHeightWithoutOverlap = lineHeightWithoutOverlap,
              yStart = highlightYStart
            )
          }
        }
      }
    }
  }
}

private fun DrawScope.drawPageHighlight(
  highlight: AyahHighlight,
  color: Color,
  totalWidth: Float,
  lineHeightWithoutOverlap: Float,
  yStart: Float
) {
  val highlightWidth = ceil((highlight.right - highlight.left) * totalWidth)
  val y = yStart + lineHeightWithoutOverlap * highlight.lineId
  val x = highlight.left * totalWidth
  drawRect(color, Offset(x, y), Size(highlightWidth, lineHeightWithoutOverlap))
}

private fun DrawScope.drawReadingBookmarkUnderline(
  highlight: AyahHighlight,
  colors: ImmutableList<Color>,
  totalWidth: Float,
  lineHeightWithoutOverlap: Float,
  yStart: Float,
  pillPath: Path
) {
  val lineBottom = yStart + lineHeightWithoutOverlap * (highlight.lineId + 1)
  val inset = BOOKMARK_INSET_RATIO * totalWidth
  val thickness = BOOKMARK_THICKNESS_RATIO * totalWidth
  val x = highlight.left * totalWidth + inset
  val y = lineBottom - BOOKMARK_BOTTOM_OFFSET_RATIO * totalWidth
  val underlineWidth = (highlight.right - highlight.left) * totalWidth - 2 * inset
  val cornerRadius = CornerRadius(thickness / 2)

  if (colors.size == 1) {
    drawRoundRect(colors.first(), Offset(x, y), Size(underlineWidth, thickness), cornerRadius)
  } else if (colors.isNotEmpty()) {
    pillPath.reset()
    pillPath.addRoundRect(RoundRect(x, y, x + underlineWidth, y + thickness, cornerRadius))

    val segmentWidth = underlineWidth / colors.size
    clipPath(pillPath) {
      for (index in 0 until colors.size) {
        val segmentStart = x + underlineWidth - segmentWidth * (index + 1)
        drawRect(colors[index], Offset(segmentStart, y), Size(segmentWidth, thickness))
      }
    }
  }
}

private fun DrawScope.resolveMarkerLayouts(
  cache: CachedMarkerLayouts,
  width: Float,
  markers: ImmutableList<AyahMarkerInfo>,
  formattedNumbers: List<String>,
  textColor: Color,
  baseTextStyle: TextStyle,
  textMeasurer: TextMeasurer
): List<TextLayoutResult> {
  if (cache.width == width &&
    cache.markers === markers &&
    cache.textColor == textColor &&
    cache.formattedLabels === formattedNumbers &&
    cache.layouts.size == markers.size
  ) {
    return cache.layouts
  }
  val smallStyle = baseTextStyle.copy(fontSize = (width * 0.0370f).toSp())
  val largeStyle = baseTextStyle.copy(fontSize = (width * 0.03f).toSp())
  val results = ArrayList<TextLayoutResult>(markers.size)
  for (i in 0 until markers.size) {
    val style = if (markers[i].ayah > 100) largeStyle else smallStyle
    results.add(textMeasurer.measure(formattedNumbers[i], style))
  }
  cache.width = width
  cache.markers = markers
  cache.textColor = textColor
  cache.formattedLabels = formattedNumbers
  cache.layouts = results
  return results
}
