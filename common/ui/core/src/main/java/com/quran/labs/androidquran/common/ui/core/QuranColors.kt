package com.quran.labs.androidquran.common.ui.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class QuranColors(
  val defaultTextColor: Color,
  val success: Color,
  val caution: Color,
  val cautionBackground: Color
)

val lightQuranColors = QuranColors(
  defaultTextColor = Color.Black,
  success = Color(0xFF34C759),
  caution = Color(0xFFA04B00),
  cautionBackground = Color(0xFFFFF4E5)
)

val darkQuranColors = QuranColors(
  defaultTextColor = Color.White,
  success = Color(0xFF30D158),
  caution = Color(0xFFFFB35C),
  cautionBackground = Color(0xFF2E2210)
)

val LocalQuranColors = staticCompositionLocalOf<QuranColors> {
  error("CompositionLocal QuranColors not present")
}
