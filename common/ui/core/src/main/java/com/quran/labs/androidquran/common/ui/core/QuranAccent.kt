package com.quran.labs.androidquran.common.ui.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The accent color the app icon lends the interface.
 *
 * @param onLight text and glyphs drawn on a filled [light] background
 * @param onDark text and glyphs drawn on a filled [dark] background
 */
@Immutable
data class QuranAccent(
  val light: Color,
  val dark: Color,
  val onLight: Color = Color.White,
  val onDark: Color
) {

  companion object {
    // keep in sync with accent_navy_gold, which xml views take through ?attr/quranAccent
    val NavyGold = QuranAccent(
      light = Color(0xFF000066),
      dark = Color(0xFFE2AF56),
      onDark = Color(0xFF000A3D)
    )

    /** The accent [QuranTheme] uses. */
    var current: QuranAccent by mutableStateOf(NavyGold)
  }
}
