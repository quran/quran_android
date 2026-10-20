package com.quran.mobile.feature.pagetheme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes

enum class PageTheme(
  val storageKey: String,
  @StringRes private val nameRes: Int,
  @ColorRes private val backgroundColorRes: Int
) {
  Light(PageThemeConstants.PAGE_THEME_LIGHT, R.string.theme_light, R.color.page_theme_light_bg),
  Sepia(PageThemeConstants.PAGE_THEME_SEPIA, R.string.page_theme_sepia, R.color.page_theme_sepia_bg);

  fun getDisplayName(context: Context): String = context.getString(nameRes)

  @ColorInt
  fun backgroundColor(context: Context): Int = context.getColor(backgroundColorRes)

  companion object {
    @JvmStatic
    fun fromKey(key: String): PageTheme = entries.firstOrNull { it.storageKey == key } ?: Light
  }
}
