package com.quran.data.page.provider.common

interface PageSizeCalculator {
  fun getWidthParameter(): String
  fun getTabletWidthParameter(): String
  fun setOverrideParameter(parameter: String)
}
