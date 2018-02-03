package com.quran.data.page.provider.impl

import com.quran.data.page.provider.PageProvider

internal class ShemerlyPageProvider : PageProvider {
  override fun getWidthParameter(): String {
    return "1200"
  }

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for shemerly pages
  }
}
