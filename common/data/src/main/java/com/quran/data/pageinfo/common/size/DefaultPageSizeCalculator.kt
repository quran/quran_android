package com.quran.data.pageinfo.common.size

import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator

open class DefaultPageSizeCalculator(displaySize: DisplaySize) : PageSizeCalculator {
  private val maxWidth: Int = if (displaySize.x > displaySize.y) displaySize.x else displaySize.y

  // override parameter can be null or 1920
  // (a few upgrade scenarios may also see this set at 1024)
  private var overrideParam: String? = null

  override fun getWidthParameter(): String {
    return overrideParam ?: when {
      maxWidth <= 320 -> "320"
      maxWidth <= 480 -> "480"
      maxWidth <= 800 -> "800"
      maxWidth <= 1280 -> "1024"
      else -> "1260"
    }
  }

  override fun getTabletWidthParameter(): String {
    return if ("1920" == overrideParam) {
      "1024"
    } else {
      getWidthParameter()
    }
  }

  override fun setOverrideParameter(parameter: String) {
    overrideParam = if (parameter.isNotBlank()) {
      parameter
    } else {
      null
    }
  }
}
