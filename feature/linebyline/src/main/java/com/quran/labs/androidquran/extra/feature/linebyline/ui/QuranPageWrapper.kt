package com.quran.labs.androidquran.extra.feature.linebyline.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelinesPosition
import java.text.NumberFormat

@Composable
fun QuranPageWrapper(
  pageInfo: PageInfo,
  pageContentType: PageContentType,
  dualScreenMode: Boolean,
  suraHeaderBitmap: ImageBitmap,
  ayahNumberFormatter: NumberFormat,
  onClick: () -> Unit,
  onPagePositioned: (Float, Float, Int, Int) -> Unit,
  onSelectionStart: (Float, Float) -> Unit,
  onSelectionModified: (Float, Float) -> Unit,
  onSelectionEnd: () -> Unit
) {
  val displaySettings = pageInfo.displaySettings

  val textBrightness = displaySettings.textBrightness.toFloat()

  val displayInfo = pageInfo.displayText
  val showHeaderFooter = displaySettings.showHeaderFooter

  val orientation = LocalConfiguration.current.orientation
  val isScrollable = (!dualScreenMode && orientation == Configuration.ORIENTATION_LANDSCAPE)

  val isNightMode = displaySettings.pageAppearance.isNightMode(isSystemInDarkTheme())
  val overlayColor = if (isNightMode) {
    Color(0x84, 0x8A, 0x91)
  } else {
    Color(0x68, 0x6E, 0x7D)
  }

  val (headerColor, ringColor, innerColor, textColor) =
    if (pageInfo.pageType.contains("1439")) {
      if (isNightMode) DARK_1439_COLORS else LIGHT_1439_COLORS
    } else {
      if (isNightMode) DARK_COLORS else LIGHT_COLORS
    }

  val ratio = (pageContentType as PageContentType.Line).ratio
  val lineHeight = pageContentType.lineHeight
  val lineColor = if (isNightMode) {
    Color.White.copy(alpha = textBrightness / 255f)
  } else {
    Color.Black
  }
  val colorFilter = remember(lineColor) { ColorFilter.tint(lineColor) }

  QuranPage(
    pageInfo,
    pageContentType,
    isScrollable,
    headerComposable = {
      if (showHeaderFooter) {
        QuranHeaderFooter(
          displayInfo.suraText,
          displayInfo.juzAreaText,
          overlayColor,
          modifier = Modifier
        )
      } else {
        Spacer(modifier = Modifier)
      }
    },
    footerComposable = {
      if (showHeaderFooter) {
        val (leftText, rightText) = if (pageInfo.page % 2 == 0) {
          displayInfo.localizedPageText to ""
        } else {
          "" to displayInfo.localizedPageText
        }

        QuranHeaderFooter(
          leftText,
          rightText,
          overlayColor,
          modifier = Modifier
        )
      } else {
        Spacer(modifier = Modifier)
      }
    },
    quranLineComposable = {
      QuranLine(
        image = it.lineImage,
        lineId = it.lineId,
        ratio,
        colorFilter = colorFilter,
        displaySettings.showLineDividers && shouldShowLineFor(pageInfo.page, it.lineId),
        lineColor
      )
    },
    suraHeaderComposable = {
      SuraHeader(
        image = suraHeaderBitmap,
        lineId = it.lineId,
        centerX = it.centerX,
        centerY = it.centerY,
        tint = headerColor,
        lineRatio = ratio
      )
    },
    ayahMarkerInfoComposable = { marker, textMeasurer ->
      AyahMarker(
        lineId = marker.lineId,
        centerX = marker.centerX,
        centerY = marker.centerY,
        ringColor = ringColor,
        innerColor = innerColor,
        text = ayahNumberFormatter.format(marker.ayah),
        // was 0.0375f for sub-100 ayat, but breaks ayah 77/78/87/88
        // note that, sometimes, this breaks the second time the activity is loaded
        textWidthRatio = if (marker.ayah > 100) 0.03f else 0.0370f,
        textColor = textColor,
        textMeasurer = textMeasurer
      )
    },
    highlightComposable = { highlight, color ->
      Highlight(
        lineId = highlight.lineId,
        left = highlight.left,
        right = highlight.right,
        color = color,
        lineRatio = ratio
      )
    },
    readingBookmarkComposable = { highlight, colors ->
      ReadingBookmarkUnderline(
        lineId = highlight.lineId,
        left = highlight.left,
        right = highlight.right,
        lineRatio = ratio,
        colors = colors
      )
    },
    sidelinesComposable = {
      Sidelines(
        pageInfo.sidelines,
        lineHeight,
        colorFilter = colorFilter,
        if (pageInfo.page % 2 == 0) SidelinesPosition.RIGHT else SidelinesPosition.LEFT
      )
    },
    onPagePositioned,
    onSelectionStart,
    onSelectionModified,
    onSelectionEnd,
    modifier = Modifier.clickable(
      interactionSource = remember { MutableInteractionSource() },
      indication = null
    ) {
      onClick()
    },
    isNightMode,
    backgroundColor = if (isNightMode) {
      val brightness = displaySettings.nightModeBackgroundBrightness
      Color(brightness, brightness, brightness)
    } else {
      displaySettings.dayBackgroundColor?.let { Color(it) }
    }
  )
}

private fun shouldShowLineFor(page: Int, line: Int): Boolean {
  return page > 3 && line > 0
}

private data class MarkerPalette(
  val headerColor: Color,
  val ringColor: Color,
  val innerColor: Color,
  val textColor: Color
)

private val DARK_1439_COLORS = MarkerPalette(
  headerColor = Color(0x73, 0xaf, 0xfa),
  ringColor = Color(0x73, 0xaf, 0xfa),
  innerColor = Color(0x17, 0x25, 0x54),
  textColor = Color(0x73, 0xaf, 0xfa)
)

private val LIGHT_1439_COLORS = MarkerPalette(
  headerColor = Color(0x25, 0x63, 0xeb),
  ringColor = Color(0x25, 0x63, 0xeb),
  innerColor = Color(0xef, 0xf6, 0xff),
  textColor = Color(0x1d, 0x4e, 0xd8)
)

private val DARK_COLORS = MarkerPalette(
  headerColor = Color(0x04, 0x78, 0x57),
  ringColor = Color(0x04, 0x78, 0x57),
  innerColor = Color(0x02, 0x2c, 0x22),
  textColor = Color(0x34, 0xd3, 0x99)
)

private val LIGHT_COLORS = MarkerPalette(
  headerColor = Color(0x04, 0x78, 0x57),
  ringColor = Color(0x04, 0x78, 0x57),
  innerColor = Color(0xec, 0xfd, 0xf5),
  textColor = Color(0x04, 0x78, 0x57)
)
