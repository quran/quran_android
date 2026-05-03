package com.quran.labs.androidquran.util

import androidx.appcompat.app.AppCompatDelegate
import com.quran.labs.androidquran.data.Constants

object ThemeUtil {

  @JvmStatic
  fun setTheme(appTheme: String, pageTheme: String) {
    val mappedTheme = when {
      pageTheme == Constants.PAGE_THEME_QUIET -> AppCompatDelegate.MODE_NIGHT_YES
      appTheme == Constants.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      appTheme == Constants.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(mappedTheme)
  }
}
