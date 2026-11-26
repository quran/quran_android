package com.quran.mobile.linebyline.ui.renderer.calculator

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

class QuranPageCalculator : PageCalculator {

  override fun calculate(widthWithoutPadding: Int, heightWithoutPadding: Int): Measurements {
    val key = (widthWithoutPadding.toLong() shl 32) or (heightWithoutPadding.toLong() and 0xFFFF_FFFFL)
    measurementsCache[key]?.let { return it }

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

    val result = Measurements(
      quranImageWidth = quranImageWidth,
      quranImageHeight = quranImageHeight,
      headerFooterWidth = headerFooterWidth,
      headerFooterHeight = headerFooterHeight
    )
    if (measurementsCache.size >= MAX_CACHE_ENTRIES) {
      measurementsCache.clear()
    }
    measurementsCache[key] = result
    return result
  }

  companion object {
    private const val MAX_CACHE_ENTRIES = 32
    private val measurementsCache = ConcurrentHashMap<Long, Measurements>()
    private const val headerFooterHeightRatio = 0.04f
    private const val quranImageMinWidthToHeightRatio = 1 / 1.60f
    private const val quranImageMaxWidthToHeightRatio = 1 / 1.84f
    private const val headerFooterMarginRatio = 0.027f
  }
}
