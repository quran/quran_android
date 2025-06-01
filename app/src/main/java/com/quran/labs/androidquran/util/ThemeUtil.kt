package com.quran.labs.androidquran.util

import androidx.appcompat.app.AppCompatDelegate
import com.quran.labs.androidquran.data.Constants

object ThemeUtil {

  fun setTheme(theme: String) {
    val mappedTheme = when (theme) {
      Constants.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      Constants.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      Constants.THEME_DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(mappedTheme)
  }
}
