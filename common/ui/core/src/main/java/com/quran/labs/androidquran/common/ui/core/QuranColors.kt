package com.quran.labs.androidquran.common.ui.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class QuranColors(
  val defaultTextColor: Color
)

val lightQuranColors = QuranColors(
  defaultTextColor = Color.Black
)

val darkQuranColors = QuranColors(
  defaultTextColor = Color.White
)

val LocalQuranColors = staticCompositionLocalOf<QuranColors> {
  error("CompositionLocal QuranColors not present")
}
