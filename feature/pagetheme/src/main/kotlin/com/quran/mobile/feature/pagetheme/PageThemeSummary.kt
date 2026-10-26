package com.quran.mobile.feature.pagetheme

import android.content.Context

fun pageThemeSummary(context: Context, settings: PageThemeSettings): String {
  val theme = PageTheme.fromKey(settings.pageTheme).getDisplayName(context)
  return when (settings.pageAppearance) {
    PageThemeConstants.PAGE_APPEARANCE_DARK -> context.getString(R.string.theme_dark)
    PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC ->
      context.getString(R.string.page_theme_summary, theme, context.getString(R.string.page_theme_match_app))
    else -> theme
  }
}
