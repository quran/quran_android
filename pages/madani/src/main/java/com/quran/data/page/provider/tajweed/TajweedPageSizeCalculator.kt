package com.quran.data.page.provider.tajweed

import com.quran.data.source.PageSizeCalculator

class TajweedPageSizeCalculator : PageSizeCalculator {
  override fun getWidthParameter() = "1280"

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for tajweed pages
  }
}
