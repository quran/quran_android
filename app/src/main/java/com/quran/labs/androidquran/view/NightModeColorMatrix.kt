package com.quran.labs.androidquran.view

/**
 * Inverts lightness (0.3r + 0.4g + 0.3b) from the background to the text brightness while keeping
 * each pixel's hue, so colored pages such as tajweed keep their colors in night mode.
 */
object NightModeColorMatrix {

  @JvmStatic
  fun create(textBrightness: Int, backgroundBrightness: Int): FloatArray {
    val scale = 1f + (textBrightness - backgroundBrightness) / 255f
    val r = 0.3f * scale
    val g = 0.4f * scale
    val b = 0.3f * scale
    val offset = textBrightness.toFloat()
    return floatArrayOf(
      1 - r, -g, -b, 0f, offset,
      -r, 1 - g, -b, 0f, offset,
      -r, -g, 1 - b, 0f, offset,
      0f, 0f, 0f, 1f, 0f
    )
  }
}
