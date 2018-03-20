package com.quran.data.page.provider.common.size

import com.quran.data.source.DisplaySize

internal class NoOverridePageSizeCalculator(displaySize: DisplaySize) :
    DefaultPageSizeCalculator(displaySize) {

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for these pages
  }
}
