package com.quran.mobile.linebyline.ui.renderer.calculator

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

class QuranPageCalculator : PageCalculator {

  override fun calculate(widthWithoutPadding: Int, heightWithoutPadding: Int): Measurements {
    val initialHeaderFooterHeight = (headerFooterHeightRatio * heightWithoutPadding).toInt()
    val initialQuranImageHeight = heightWithoutPadding - 2 * initialHeaderFooterHeight
    val computedWidth = floor(initialQuranImageHeight * quranImageMinWidthToHeightRatio).toInt()
    val quranImageWidth = min(widthWithoutPadding, computedWidth)
    val maxImageHeight = round(quranImageWidth / quranImageMaxWidthToHeightRatio).toInt()

    val headerFooterHeight: Int
    val quranImageHeight: Int
    if (initialQuranImageHeight > maxImageHeight) {
      headerFooterHeight =
        round((maxImageHeight + 2 * initialHeaderFooterHeight) * headerFooterHeightRatio).toInt()
      quranImageHeight = maxImageHeight
    } else {
      quranImageHeight = initialQuranImageHeight
      headerFooterHeight = initialHeaderFooterHeight
    }
    val headerMargin = (quranImageWidth * headerFooterMarginRatio).toInt()
    val headerFooterWidth = quranImageWidth - (2 * headerMargin)

    return Measurements(
      quranImageWidth = quranImageWidth,
      quranImageHeight = quranImageHeight,
      headerFooterWidth = headerFooterWidth,
      headerFooterHeight = headerFooterHeight
    )
  }

  companion object {
    private const val headerFooterHeightRatio = 0.04f
    private const val quranImageMinWidthToHeightRatio = 1 / 1.60f
    private const val quranImageMaxWidthToHeightRatio = 1 / 1.84f
    private const val headerFooterMarginRatio = 0.027f
  }
}
