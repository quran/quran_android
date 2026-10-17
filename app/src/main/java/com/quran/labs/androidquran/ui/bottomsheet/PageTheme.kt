package com.quran.labs.androidquran.ui.bottomsheet

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants

enum class PageTheme(
  val storageKey: String,
  @StringRes private val nameRes: Int,
  @ColorRes val backgroundColorRes: Int,
  @ColorRes val textColorRes: Int
) {
  Paper(Constants.PAGE_THEME_PAPER, R.string.page_theme_paper, R.color.theme_paper_bg, R.color.theme_paper_text),
  Original(Constants.PAGE_THEME_ORIGINAL, R.string.page_theme_original, R.color.theme_original_bg, R.color.theme_original_text),
  Quiet(Constants.PAGE_THEME_QUIET, R.string.page_theme_quiet, R.color.theme_quiet_bg, R.color.theme_quiet_text),
  Calm(Constants.PAGE_THEME_CALM, R.string.page_theme_calm, R.color.theme_calm_bg, R.color.theme_calm_text),
  Focus(Constants.PAGE_THEME_FOCUS, R.string.page_theme_focus, R.color.theme_focus_bg, R.color.theme_focus_text);

  fun getDisplayName(context: Context): String = context.getString(nameRes)

  companion object {
    @JvmStatic
    fun fromKey(key: String): PageTheme = entries.firstOrNull { it.storageKey == key } ?: Paper
  }
}
