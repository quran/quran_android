package com.quran.labs.androidquran.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
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

  fun applyThemeStyle(style: AppThemeStyle) {
    AppThemeStyle.select(style)
  }

  @StyleRes
  fun themeOverlayRes(style: AppThemeStyle): Int = when (style) {
    AppThemeStyle.DEFAULT -> R.style.ThemeOverlay_Quran_Default
    AppThemeStyle.GREEN -> R.style.ThemeOverlay_Quran_Green
    AppThemeStyle.BLUE -> R.style.ThemeOverlay_Quran_Blue
    AppThemeStyle.BEIGE -> R.style.ThemeOverlay_Quran_Beige
    AppThemeStyle.GRAY -> R.style.ThemeOverlay_Quran_Gray
  }

  @StyleRes
  fun dialogThemeRes(style: AppThemeStyle = AppThemeStyle.current): Int = when (style) {
    AppThemeStyle.DEFAULT -> R.style.QuranDialogTheme_Default
    AppThemeStyle.GREEN -> R.style.QuranDialogTheme_Green
    AppThemeStyle.BLUE -> R.style.QuranDialogTheme_Blue
    AppThemeStyle.BEIGE -> R.style.QuranDialogTheme_Beige
    AppThemeStyle.GRAY -> R.style.QuranDialogTheme_Gray
  }

  @StyleRes
  fun popupThemeRes(style: AppThemeStyle = AppThemeStyle.current): Int = when (style) {
    AppThemeStyle.DEFAULT -> R.style.ThemeOverlay_Quran_Popup_Default
    AppThemeStyle.GREEN -> R.style.ThemeOverlay_Quran_Popup_Green
    AppThemeStyle.BLUE -> R.style.ThemeOverlay_Quran_Popup_Blue
    AppThemeStyle.BEIGE -> R.style.ThemeOverlay_Quran_Popup_Beige
    AppThemeStyle.GRAY -> R.style.ThemeOverlay_Quran_Popup_Gray
  }

  fun applyActivityTheme(activity: Activity): AppThemeStyle {
    val settings = QuranSettings.getInstance(activity)
    val intentExtraStyle = if (BuildConfig.DEBUG) {
      activity.intent?.getStringExtra(Constants.PREF_APP_THEME_STYLE)
    } else {
      null
    }
    val style = if (!intentExtraStyle.isNullOrBlank()) {
      val resolved = AppThemeStyle.fromPreference(intentExtraStyle)
      settings.appThemeStyle = resolved.storageKey
      resolved
    } else {
      AppThemeStyle.fromPreference(settings.appThemeStyle)
    }
    AppThemeStyle.select(style)
    activity.theme.applyStyle(themeOverlayRes(style), true)
    val surfaceColor = resolveThemeColor(activity, R.attr.quranSurface, Color.TRANSPARENT)
    if (surfaceColor != Color.TRANSPARENT) {
      activity.window?.setBackgroundDrawable(ColorDrawable(surfaceColor))
    }
    return style
  }

  fun applyActionModeStatusGuard(activity: Activity) {
    /**
     * AppCompatDelegate adds a status guard View under the status bar when ActionMode starts
     * (white in light mode, black in dark mode), breaking edge-to-edge themed headers.
     */
    val abRoot = activity.findViewById<ViewGroup>(androidx.appcompat.R.id.action_bar_root) ?: return
    abRoot.post {
      val statusGuard = abRoot.getChildAt(abRoot.childCount - 1)
      statusGuard?.let {
        if (statusGuard::class == View::class && statusGuard.top == 0) {
          statusGuard.setBackgroundColor(
            resolveThemeColor(
              activity,
              R.attr.quranToolbarBackground,
              ContextCompat.getColor(activity, R.color.toolbar)
            )
          )
        }
      }
    }
  }

  fun styleSnackbar(snackbar: Snackbar) {
    val context = snackbar.view.context
    val backgroundColor = resolveThemeColor(
      context,
      R.attr.quranSurfaceElevated,
      ContextCompat.getColor(context, R.color.snackbar_background_color)
    )
    val textColor = resolveThemeColor(
      context,
      R.attr.quranTextPrimary,
      ContextCompat.getColor(context, R.color.default_text)
    )
    val accentColor = resolveThemeColor(
      context,
      R.attr.quranAccent,
      ContextCompat.getColor(context, R.color.accent_navy_gold)
    )
    snackbar.view.setBackgroundColor(backgroundColor)
    snackbar.setTextColor(textColor)
    snackbar.setActionTextColor(accentColor)
  }

  fun enableThemedEdgeToEdge(activity: ComponentActivity) {
    val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val isLightToolbar = resolveThemeBoolean(
      activity,
      R.attr.quranIsLightToolbar,
      fallback = !isNight
    )
    activity.enableEdgeToEdge(
      statusBarStyle = if (isLightToolbar) {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
      } else {
        SystemBarStyle.dark(Color.TRANSPARENT)
      },
      navigationBarStyle = if (isNight) {
        SystemBarStyle.dark(Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
      }
    )
  }

  @JvmStatic
  @ColorInt
  fun resolveThemeColor(
    context: Context,
    @AttrRes attrRes: Int,
    @ColorInt fallback: Int
  ): Int {
    val typedValue = TypedValue()
    return if (context.theme.resolveAttribute(attrRes, typedValue, true)) {
      if (typedValue.resourceId != 0) {
        context.getColor(typedValue.resourceId)
      } else {
        typedValue.data
      }
    } else {
      fallback
    }
  }

  @JvmStatic
  fun resolveThemeBoolean(
    context: Context,
    @AttrRes attrRes: Int,
    fallback: Boolean
  ): Boolean {
    val typedValue = TypedValue()
    return if (context.theme.resolveAttribute(attrRes, typedValue, true) &&
      typedValue.type == TypedValue.TYPE_INT_BOOLEAN
    ) {
      typedValue.data != 0
    } else {
      fallback
    }
  }
}
