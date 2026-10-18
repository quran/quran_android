package com.quran.mobile.feature.pagetheme

interface PageThemeSettings {
  var pageTheme: String
  var pageAppearance: String
  val isPageAppearanceDark: Boolean
  var nightModeTextBrightness: Int
  var nightModeBackgroundBrightness: Int
  val originalNightTextBrightness: Int

  fun hasOldBackgroundOption(): Boolean
  fun useOldBackground(): Boolean
  fun setUseOldBackground(useOldBackground: Boolean)
}
