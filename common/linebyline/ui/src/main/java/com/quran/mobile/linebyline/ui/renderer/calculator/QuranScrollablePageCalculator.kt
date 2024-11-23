package com.quran.mobile.linebyline.ui.renderer.calculator

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

class QuranScrollablePageCalculator(private val density: Float) : PageCalculator {

  override fun calculate(widthWithoutPadding: Int, heightWithoutPadding: Int): Measurements {
    val quranImageWidthRatio = quranImageWidthRatioWhenScrollable
    val quranImageWidth = min(
      (quranImageMaximumWidthWhenScrollable * density).toInt(),
      round(widthWithoutPadding * quranImageWidthRatio).toInt()
    )
    val quranImageHeight = ceil(quranImageWidth * quranImageHeightToWidthRatioWhenScrollable).toInt()
    val headerFooterHeight = (quranImageWidth * headerFooterHeightRatioWhenScrollable).toInt()

    // this is how we get the width to be the width of the text within the image instead of
    // the image and highlight area itself.
    val headerMargin = (quranImageWidth * headerFooterMarginRatio).toInt()
    val headerFooterWidth = quranImageWidth - (2 * headerMargin)

    return Measurements(
      quranImageWidth = quranImageWidth,
      quranImageHeight = quranImageHeight,
      // header and footer are the same width as the image
      headerFooterWidth = headerFooterWidth,
      headerFooterHeight = headerFooterHeight
    )
  }

  companion object {
    private const val quranImageWidthRatioWhenScrollable = 0.97f
    private const val quranImageMaximumWidthWhenScrollable = 1080
    private const val quranImageHeightToWidthRatioWhenScrollable = 1.76f
    private const val headerFooterHeightRatioWhenScrollable = 0.04f
    private const val headerFooterMarginRatio = 0.027f
  }
}
