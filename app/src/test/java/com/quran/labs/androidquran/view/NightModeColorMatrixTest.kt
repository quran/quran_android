package com.quran.labs.androidquran.view

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class NightModeColorMatrixTest {

  @Test
  fun blackBecomesTextAndWhiteBecomesBackground() {
    listOf(255 to 0, 150 to 32, 120 to 0, 230 to 64).forEach { (text, background) ->
      val matrix = NightModeColorMatrix.create(text, background)
      assertThat(matrix.applyTo(0x000000)).isEqualTo(gray(text))
      assertThat(matrix.applyTo(0xFFFFFF)).isEqualTo(gray(background))
    }
  }

  @Test
  fun grayscaleMatchesPlainInversionAtDefaultBrightness() {
    val matrix = NightModeColorMatrix.create(255, 0)
    for (level in 0..255) {
      assertThat(matrix.applyTo(gray(level))).isEqualTo(gray(255 - level))
    }
  }

  @Test
  fun tajweedColorsKeepTheirHueAndStayReadable() {
    val matrix = NightModeColorMatrix.create(255, 0)
    tajweedColors.forEach { color ->
      val night = matrix.applyTo(color)
      val hueShift = abs(hue(night) - hue(color)).let { min(it, 360 - it) }
      assertThat(hueShift).isLessThan(10f)
      assertThat(contrastOnBlack(night)).isAtLeast(4.5)
    }
  }

  private val tajweedColors = listOf(
    0x04943C, // ghunnah
    0x046C94, // tafkheem
    0x049CE4, // qalqalah
    0xE5057A, // madd 4/5
    0xB1055F, // madd 6
    0xEE7A06, // madd 2/4/6
    0xC4A434, // madd 2
  )

  private fun gray(level: Int) = (level shl 16) or (level shl 8) or level

  private fun FloatArray.applyTo(rgb: Int): Int {
    val input = floatArrayOf(
      (rgb shr 16 and 0xFF).toFloat(), (rgb shr 8 and 0xFF).toFloat(), (rgb and 0xFF).toFloat(), 255f
    )
    return (0..2).fold(0) { result, row ->
      val value = (0..3).sumOf { this[row * 5 + it].toDouble() * input[it] } + this[row * 5 + 4]
      (result shl 8) or value.roundToInt().coerceIn(0, 255)
    }
  }

  private fun hue(rgb: Int): Float {
    val r = rgb shr 16 and 0xFF
    val g = rgb shr 8 and 0xFF
    val b = rgb and 0xFF
    val maximum = max(r, max(g, b))
    val delta = (maximum - min(r, min(g, b))).toFloat()
    return when (maximum) {
      r -> 60 * ((g - b) / delta).mod(6f)
      g -> 60 * ((b - r) / delta + 2)
      else -> 60 * ((r - g) / delta + 4)
    }
  }

  private fun contrastOnBlack(rgb: Int): Double {
    fun linear(channel: Int): Double {
      val c = channel / 255.0
      return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * linear(rgb shr 16 and 0xFF) +
        0.7152 * linear(rgb shr 8 and 0xFF) +
        0.0722 * linear(rgb and 0xFF)
    return (luminance + 0.05) / 0.05
  }
}
