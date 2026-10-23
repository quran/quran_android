package com.quran.labs.androidquran.util

import android.content.SharedPreferences
import androidx.core.content.edit
import com.quran.labs.androidquran.data.Constants
import com.quran.mobile.feature.pagetheme.PageThemeConstants

object PageThemePreferences {

  @JvmStatic
  fun migrate(preferences: SharedPreferences, hasPreviousVersion: Boolean) {
    if (preferences.contains(Constants.PREF_PAGE_APPEARANCE)) return

    val isUpgrade = hasPreviousVersion ||
        preferences.contains(Constants.PREF_LAST_PAGE) ||
        preferences.contains(Constants.PREF_NIGHT_MODE) ||
        preferences.contains(Constants.PREF_USE_NEW_BACKGROUND)

    preferences.edit {
      if (isUpgrade) {
        val nightMode = preferences.getBoolean(Constants.PREF_NIGHT_MODE, false)
        putString(
          Constants.PREF_PAGE_APPEARANCE,
          if (nightMode) PageThemeConstants.PAGE_APPEARANCE_DARK else PageThemeConstants.PAGE_APPEARANCE_LIGHT
        )
        if (!preferences.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
          putBoolean(Constants.PREF_USE_OLD_BACKGROUND, true)
        }
      } else {
        putString(Constants.PREF_PAGE_APPEARANCE, PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
      }
    }
  }
}
