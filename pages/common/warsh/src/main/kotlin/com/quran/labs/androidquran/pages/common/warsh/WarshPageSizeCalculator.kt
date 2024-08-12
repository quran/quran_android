package com.quran.labs.androidquran.pages.common.warsh

import com.quran.data.source.PageSizeCalculator

class WarshPageSizeCalculator : PageSizeCalculator {
  override fun getWidthParameter() = "1188"

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for warsh pages
  }
}
