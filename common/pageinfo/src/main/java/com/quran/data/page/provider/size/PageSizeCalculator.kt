package com.quran.data.page.provider.size

interface PageSizeCalculator {
  fun getWidthParameter(): String
  fun getTabletWidthParameter(): String
  fun setOverrideParameter(parameter: String)
}
