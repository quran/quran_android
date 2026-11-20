package com.quran.data.page.provider.newmadani

import com.quran.data.source.PageSizeCalculator

internal class NewMadaniPageSizeCalculator(private val width: Int = 1120) : PageSizeCalculator {
  override fun getWidthParameter() = width.toString()

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for new madani pages
  }
}
