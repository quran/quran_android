package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType

val HighlightType.isUnderText: Boolean
  get() = this is HighlightType.Highlight || this is HighlightType.Recitation

private const val NightHighlightAlpha = 0.40f

// as on image pages, whose night filter would otherwise make these marks glare
private const val NightRecitationAlpha = 0x66 / 255f

@Composable
fun HighlightType.color(isNightMode: Boolean): Color {
  return if (this is HighlightType.Recitation) {
    val color = colorResource(if (isNightMode) nightColor else color)
    if (isNightMode) color.copy(alpha = NightRecitationAlpha) else color
  } else {
    paintColor(isNightMode)
  }
}

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
    // a resource: see color()
    is HighlightType.Recitation -> Color.Unspecified
  }
}

fun ReadingBookmarkType.paintColor(isNightMode: Boolean): Color {
  return when (this) {
    ReadingBookmarkType.TEAL -> if (isNightMode) Color(0xFF4DB6AC) else Color(0xFF00897B)
    ReadingBookmarkType.ORANGE -> if (isNightMode) Color(0xFFFFB74D) else Color(0xFFEF6C00)
    ReadingBookmarkType.RED -> if (isNightMode) Color(0xFFE57373) else Color(0xFFC62828)
    ReadingBookmarkType.GREEN -> if (isNightMode) Color(0xFF81C784) else Color(0xFF2E7D32)
    ReadingBookmarkType.BLUE -> if (isNightMode) Color(0xFF64B5F6) else Color(0xFF1565C0)
    ReadingBookmarkType.PURPLE -> if (isNightMode) Color(0xFFBA68C8) else Color(0xFF6A1B9A)
    ReadingBookmarkType.BROWN -> if (isNightMode) Color(0xFFA1887F) else Color(0xFF6D4C41)
    ReadingBookmarkType.GREY -> if (isNightMode) Color(0xFF90A4AE) else Color(0xFF546E7A)
  }
}
