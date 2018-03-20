package com.quran.data.page.provider.shemerly

import com.quran.data.source.PageSizeCalculator

internal class ShemerlyPageSizeCalculator : PageSizeCalculator {

  override fun getWidthParameter() = "1200"

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for shemerly pages
  }
}
