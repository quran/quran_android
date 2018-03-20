package com.quran.data.source

interface PageSizeCalculator {
  fun getWidthParameter(): String
  fun getTabletWidthParameter(): String
  fun setOverrideParameter(parameter: String)
}
