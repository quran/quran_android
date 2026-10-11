package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.ui.graphics.Color
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType

val HighlightType.isUnderText: Boolean
  get() = this is HighlightType.Highlight

private const val NightHighlightAlpha = 0.40f

fun HighlightType.paintColor(isNightMode: Boolean): Color {
  return when (this) {
    HighlightType.Selection -> Color(0x46, 0x94, 0xa6, 0x40)
    HighlightType.Audio -> Color(0x46, 0xa6, 0x46, 0x40)
    HighlightType.AudioWord -> Color(0xff, 0xb3, 0x3d, 0x60)
    HighlightType.Bookmark -> Color(0xa4, 0xa4, 0xa4, 0x40)
    is HighlightType.Highlight -> {
      val paletteColor = HighlightColors[color].color
      if (isNightMode) paletteColor.copy(alpha = NightHighlightAlpha) else paletteColor
    }
  }
}

fun ReadingBookmarkType.paintColor(isNightMode: Boolean): Color {
  return when (this) {
    ReadingBookmarkType.GREEN -> if (isNightMode) Color(0xFF34D3A4) else Color(0xFF05956F)
    ReadingBookmarkType.PURPLE -> if (isNightMode) Color(0xFFD477FF) else Color(0xFFB315FC)
    ReadingBookmarkType.BLUE -> if (isNightMode) Color(0xFF62AEFF) else Color(0xFF007FF0)
  }
}
