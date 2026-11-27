package com.quran.labs.androidquran.view

/**
 * Inverts lightness (0.3r + 0.4g + 0.3b) from the background to the text brightness while keeping
 * each pixel's hue, so colored pages such as tajweed keep their colors in night mode.
 */
object NightModeColorMatrix {

  @JvmStatic
  fun create(textBrightness: Int, backgroundBrightness: Int): FloatArray =
    create(textBrightness, backgroundBrightness, backgroundBrightness, backgroundBrightness)

  @JvmStatic
  fun create(
    textBrightness: Int,
    bgRed: Int,
    bgGreen: Int,
    bgBlue: Int
  ): FloatArray {
    val scaleR = 1f + (textBrightness - bgRed) / 255f
    val scaleG = 1f + (textBrightness - bgGreen) / 255f
    val scaleB = 1f + (textBrightness - bgBlue) / 255f
    val offset = textBrightness.toFloat()
    return floatArrayOf(
      1f - 0.3f * scaleR, -0.4f * scaleR, -0.3f * scaleR, 0f, offset,
      -0.3f * scaleG, 1f - 0.4f * scaleG, -0.3f * scaleG, 0f, offset,
      -0.3f * scaleB, -0.4f * scaleB, 1f - 0.3f * scaleB, 0f, offset,
      0f, 0f, 0f, 1f, 0f
    )
  }
}
