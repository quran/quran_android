package com.quran.mobile.feature.pagetheme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes

enum class PageTheme(
  val storageKey: String,
  @StringRes private val nameRes: Int,
  @ColorRes private val backgroundColorRes: Int,
  @ColorRes private val textColorRes: Int
) {
  Paper(PageThemeConstants.PAGE_THEME_PAPER, R.string.page_theme_paper, R.color.theme_paper_bg, R.color.theme_paper_text),
  Original(PageThemeConstants.PAGE_THEME_ORIGINAL, R.string.page_theme_original, R.color.theme_original_bg, R.color.theme_original_text),
  Quiet(PageThemeConstants.PAGE_THEME_QUIET, R.string.page_theme_quiet, R.color.theme_quiet_bg, R.color.theme_quiet_text),
  Calm(PageThemeConstants.PAGE_THEME_CALM, R.string.page_theme_calm, R.color.theme_calm_bg, R.color.theme_calm_text),
  Focus(PageThemeConstants.PAGE_THEME_FOCUS, R.string.page_theme_focus, R.color.theme_focus_bg, R.color.theme_focus_text);

  fun getDisplayName(context: Context): String = context.getString(nameRes)

  @ColorInt
  fun backgroundColor(context: Context, dark: Boolean): Int =
    paletteContext(context, dark).getColor(backgroundColorRes)

  @ColorInt
  fun textColor(context: Context, dark: Boolean): Int =
    paletteContext(context, dark).getColor(textColorRes)

  companion object {
    @JvmStatic
    fun fromKey(key: String): PageTheme = entries.firstOrNull { it.storageKey == key } ?: Original

    private fun paletteContext(context: Context, dark: Boolean): Context {
      val config = Configuration(context.resources.configuration)
      config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
          if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      return context.createConfigurationContext(config)
    }
  }
}
