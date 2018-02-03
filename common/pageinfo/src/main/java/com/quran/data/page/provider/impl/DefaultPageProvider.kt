package com.quran.data.page.provider.impl

import android.graphics.Point
import android.view.Display
import com.quran.data.page.provider.PageProvider

internal class DefaultPageProvider(display: Display) : PageProvider {

  private val maxWidth: Int
  private var overrideParam: String? = null

  init {
    val point = Point()
    display.getSize(point)
    maxWidth = if (point.x > point.y) point.x else point.y
  }

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
