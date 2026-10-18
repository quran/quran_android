package com.quran.mobile.feature.pagetheme

import android.content.Context

internal val pageAppearanceLabels = listOf(
  PageThemeConstants.PAGE_APPEARANCE_LIGHT to R.string.theme_light,
  PageThemeConstants.PAGE_APPEARANCE_DARK to R.string.theme_dark,
  PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC to R.string.theme_automatic
)

fun pageThemeSummary(context: Context, settings: PageThemeSettings): String {
  val theme = PageTheme.fromKey(settings.pageTheme).getDisplayName(context)
  val appearance = pageAppearanceLabels.firstOrNull { it.first == settings.pageAppearance }?.second
    ?: R.string.theme_light
  return context.getString(R.string.page_theme_summary, theme, context.getString(appearance))
}
