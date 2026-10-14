package com.quran.labs.androidquran.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import com.quran.labs.androidquran.data.Constants

object ThemeUtil {

  @JvmStatic
  fun setTheme(appTheme: String, pageTheme: String) {
    AppCompatDelegate.setDefaultNightMode(appNightMode(appTheme, pageTheme))
  }

  fun appNightMode(appTheme: String, pageTheme: String): Int = when {
      pageTheme == Constants.PAGE_THEME_QUIET -> AppCompatDelegate.MODE_NIGHT_YES
      appTheme == Constants.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      appTheme == Constants.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
  }

  // Quiet forces dark app chrome, but its gray/black page palette follows the saved appearance.
  @JvmStatic
  fun isDarkAppearance(appTheme: String, systemNight: Boolean): Boolean = when (appTheme) {
    Constants.THEME_LIGHT -> false
    Constants.THEME_DARK -> true
    else -> systemNight
  }

  @JvmStatic
  fun paletteContext(context: Context, appTheme: String): Context {
    val systemNight = Resources.getSystem().configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val config = Configuration(context.resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (isDarkAppearance(appTheme, systemNight)) Configuration.UI_MODE_NIGHT_YES
        else Configuration.UI_MODE_NIGHT_NO
    return context.createConfigurationContext(config)
  }
}
