package com.quran.labs.androidquran.extra.feature.linebyline.model

import com.quran.data.model.PageAppearance

data class DisplaySettings(
  val pageAppearance: PageAppearance,
  val textBrightness: Int,
  val nightModeBackgroundBrightness: Int,
  val dayBackgroundColor: Int?,
  val showHeaderFooter: Boolean,
  val showSidelines: Boolean,
  val showLineDividers: Boolean,
  val appTheme: String = "default"
) {
  fun isNightMode(systemNight: Boolean): Boolean {
    val isAppDark = when (appTheme) {
      "light" -> false
      "dark" -> true
      else -> systemNight
    }
    return pageAppearance.isNightMode(isAppDark)
  }
}
