package com.quran.mobile.feature.pagetheme

import kotlin.math.ln1p
import kotlin.math.min

class FakePageThemeSettings : PageThemeSettings {
  override var pageTheme: String = PageThemeConstants.PAGE_THEME_LIGHT
  override var pageAppearance: String = PageThemeConstants.PAGE_APPEARANCE_LIGHT
  var isAppDark: Boolean = false
  override val isNightMode: Boolean
    get() = when (pageAppearance) {
      PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC -> isAppDark
      else -> pageAppearance == PageThemeConstants.PAGE_APPEARANCE_DARK
    }
  override var nightModeTextBrightness: Int = 255
  override var nightModeBackgroundBrightness: Int = 0
  override val pageTextBrightness: Int
    get() = min(255, (50 * ln1p(nightModeBackgroundBrightness.toDouble()) + nightModeTextBrightness).toInt())

  var oldBackground: Boolean? = null

  override fun hasOldBackgroundOption(): Boolean = oldBackground != null
  override fun useOldBackground(): Boolean = oldBackground == true
  override fun setUseOldBackground(useOldBackground: Boolean) {
    oldBackground = useOldBackground
  }
}
