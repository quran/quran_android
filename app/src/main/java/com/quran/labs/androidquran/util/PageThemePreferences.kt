package com.quran.labs.androidquran.util

import android.content.SharedPreferences
import com.quran.labs.androidquran.data.Constants

object PageThemePreferences {
  // Preserve saved appearance and old values: custom brightness has no exact preset equivalent.
  @JvmStatic
  fun migrate(preferences: SharedPreferences) {
    if (preferences.contains(Constants.PREF_PAGE_THEME)) return
    if (!preferences.contains("nightMode") && !preferences.contains("useNewBackground")) return

    val theme = when {
      preferences.getBoolean("nightMode", false) -> Constants.PAGE_THEME_QUIET
      preferences.getBoolean("useNewBackground", true) -> Constants.PAGE_THEME_PAPER
      else -> Constants.PAGE_THEME_ORIGINAL
    }
    preferences.edit().putString(Constants.PREF_PAGE_THEME, theme).apply()
  }
}
