package com.quran.labs.androidquran.extra.feature.linebyline.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlin.math.ln1p
import kotlin.math.min

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

  val originalTextBrightness = displaySettings.textBrightness.toFloat()
  val backgroundBrightness = displaySettings.nightModeBackgroundBrightness
  // avoid damaging the looks of the Quran page
  val adjustedBrightness = (50 * ln1p(backgroundBrightness.toDouble()) + originalTextBrightness).toInt()
  val textBrightness = min(adjustedBrightness.toFloat(), 255f)

  val overlayColor = Color(0x68, 0x6E, 0x7D)

  val displayInfo = pageInfo.displayText
  val showHeaderFooter = displaySettings.showHeaderFooter

  val orientation = LocalConfiguration.current.orientation
  val isScrollable = (!dualScreenMode && orientation == Configuration.ORIENTATION_LANDSCAPE)

  val isNightMode = displaySettings.isNightMode
  val (headerColor, ringColor, innerColor, textColor) =
    if (pageInfo.pageType.contains("1439")) {
      if (isNightMode) {
        listOf(
          // header color
          Color(0x73, 0xaf, 0xfa),
          // ring color
          Color(0x73, 0xaf, 0xfa),
          // inner color
          Color(0x17, 0x25, 0x54),
          // text color
          Color(0x73, 0xaf, 0xfa)
        )
      } else {
        listOf(
          // header color
          Color(0x25, 0x63, 0xeb),
          // ring color
          Color(0x25, 0x63, 0xeb),
          // inner color
          Color(0xef, 0xf6, 0xff),
          // text color
          Color(0x1d, 0x4e, 0xd8)
        )
      }
    } else {
      if (isNightMode) {
        listOf(
          // header color
          Color(0x04, 0x78, 0x57),
          // ring color
          Color(0x04, 0x78, 0x57),
          // inner color
          Color(0x02, 0x2c, 0x22),
          // text color
          Color(0x34, 0xd3, 0x99)
        )
      } else {
        listOf(
          // header color
          Color(0x04, 0x78, 0x57),
          // ring color
          Color(0x04, 0x78, 0x57),
          // inner color
          Color(0xec, 0xfd, 0xf5),
          // text color
          Color(0x04, 0x78, 0x57)
        )
      }
    }

  val ratio = (pageContentType as PageContentType.Line).ratio
  val lineHeight = pageContentType.lineHeight
  val lineColor = if (displaySettings.isNightMode) {
    Color.White.copy(alpha = textBrightness / 255f)
  } else {
    Color.Black
  }
  val colorFilter = ColorFilter.tint(lineColor)

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
    ayahMarkerInfoComposable = {
      AyahMarker(
        lineId = it.lineId,
        centerX = it.centerX,
        centerY = it.centerY,
        ringColor = ringColor,
        innerColor = innerColor,
        text = ayahNumberFormatter.format(it.ayah),
        // was 0.0375f for sub-100 ayat, but breaks ayah 77/78/87/88
        // note that, sometimes, this breaks the second time the activity is loaded
        textWidthRatio = if (it.ayah > 100) 0.03f else 0.0370f,
        textColor = textColor
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
    nightModeBackgroundBrightness = backgroundBrightness
  )
}

private fun shouldShowLineFor(page: Int, line: Int): Boolean {
  return page > 3 && line > 0
}
