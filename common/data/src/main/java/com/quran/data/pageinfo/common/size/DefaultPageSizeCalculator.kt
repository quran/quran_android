package com.quran.data.pageinfo.common.size

import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator

open class DefaultPageSizeCalculator(displaySize: DisplaySize) : PageSizeCalculator {
  private val maxWidth: Int = if (displaySize.x > displaySize.y) displaySize.x else displaySize.y
  private var overrideParam: String? = null

  override fun getWidthParameter(): String {
    return when {
      maxWidth <= 320 -> "320"
      maxWidth <= 480 -> "480"
      maxWidth <= 800 -> "800"
      maxWidth <= 1280 -> "1024"
      else -> overrideParam ?: "1260"
    }
  }

  override fun getTabletWidthParameter(): String {
    return if ("1260" == getWidthParameter()) {
      // for tablet, if the width is more than 1280, use 1260
      // images for both dimens (only applies to new installs)
      "1260"
    } else {
      getBestTabletLandscapeSizeMatch(maxWidth / 2)
    }
  }

  override fun setOverrideParameter(parameter: String) {
    if (parameter.isNotBlank()) {
      overrideParam = parameter
    }
  }

  private fun getBestTabletLandscapeSizeMatch(width: Int): String {
    return if (width <= 640) {
      "512"
    } else {
      "1024"
    }
  }
}
