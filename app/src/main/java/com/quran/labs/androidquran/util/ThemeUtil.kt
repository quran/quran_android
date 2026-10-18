package com.quran.labs.androidquran.util

import android.content.Context
import android.content.res.Configuration
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

  @JvmStatic
  fun isDarkAppearance(appTheme: String, systemNight: Boolean): Boolean = when (appTheme) {
    Constants.THEME_LIGHT -> false
    Constants.THEME_DARK -> true
    else -> systemNight
  }

  @JvmStatic
  fun paletteContext(context: Context, dark: Boolean): Context {
    val config = Configuration(context.resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return context.createConfigurationContext(config)
  }
}
