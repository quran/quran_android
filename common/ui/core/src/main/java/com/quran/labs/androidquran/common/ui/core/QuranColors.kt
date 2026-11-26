package com.quran.labs.androidquran.common.ui.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class QuranColors(
  val defaultTextColor: Color,
  val success: Color,
  val caution: Color,
  val cautionBackground: Color,
  val cautionSeparator: Color,
  val toolbarBackground: Color = AppThemeStyle.DEFAULT.lightPalette.toolbarBackground,
  val onToolbar: Color = AppThemeStyle.DEFAULT.lightPalette.onToolbar,
  val onToolbarSecondary: Color = AppThemeStyle.DEFAULT.lightPalette.onToolbarSecondary,
  val surfaceElevated: Color = AppThemeStyle.DEFAULT.lightPalette.surfaceElevated,
  val divider: Color = AppThemeStyle.DEFAULT.lightPalette.divider,
  val searchHighlight: Color = AppThemeStyle.DEFAULT.lightPalette.searchHighlight,
  val isLightToolbar: Boolean = true,
  val cardSurface: Color = AppThemeStyle.DEFAULT.lightPalette.cardSurface,
  val glassBg: Color = AppThemeStyle.DEFAULT.lightPalette.glassBg,
  val glassBorder: Color = AppThemeStyle.DEFAULT.lightPalette.glassBorder,
  val mushafOverlayBg: Color = AppThemeStyle.DEFAULT.lightPalette.mushafOverlayBg,
  val navBg: Color = AppThemeStyle.DEFAULT.lightPalette.navBg,
  val navBorder: Color = AppThemeStyle.DEFAULT.lightPalette.navBorder,
  val ctaBackground: Color = AppThemeStyle.DEFAULT.lightPalette.ctaBackground,
  val onCta: Color = AppThemeStyle.DEFAULT.lightPalette.onCta,
  val ayahHighlightFill: Color = AppThemeStyle.DEFAULT.lightPalette.ayahHighlightFill,
  val editorialAccent: Color = AppThemeStyle.DEFAULT.lightPalette.editorialAccent,
  val surfaceTopGlow: Color = AppThemeStyle.DEFAULT.lightPalette.surfaceTopGlow,
  val glassFloatBg: Color = AppThemeStyle.DEFAULT.lightPalette.glassFloatBg,
  val shadowTint: Color = AppThemeStyle.DEFAULT.lightPalette.shadowTint
)

val lightQuranColors = QuranColors(
  defaultTextColor = Color.Black,
  success = Color(0xFF137333),
  caution = Color(0xFFA04B00),
  cautionBackground = Color(0xFFFFF4E5),
  cautionSeparator = Color(0xFFF3DDBF),
  toolbarBackground = AppThemeStyle.DEFAULT.lightPalette.toolbarBackground,
  onToolbar = AppThemeStyle.DEFAULT.lightPalette.onToolbar,
  onToolbarSecondary = AppThemeStyle.DEFAULT.lightPalette.onToolbarSecondary,
  surfaceElevated = AppThemeStyle.DEFAULT.lightPalette.surfaceElevated,
  divider = AppThemeStyle.DEFAULT.lightPalette.divider,
  searchHighlight = AppThemeStyle.DEFAULT.lightPalette.searchHighlight,
  isLightToolbar = AppThemeStyle.DEFAULT.lightPalette.isLightToolbar,
  cardSurface = AppThemeStyle.DEFAULT.lightPalette.cardSurface,
  glassBg = AppThemeStyle.DEFAULT.lightPalette.glassBg,
  glassBorder = AppThemeStyle.DEFAULT.lightPalette.glassBorder,
  mushafOverlayBg = AppThemeStyle.DEFAULT.lightPalette.mushafOverlayBg,
  navBg = AppThemeStyle.DEFAULT.lightPalette.navBg,
  navBorder = AppThemeStyle.DEFAULT.lightPalette.navBorder,
  ctaBackground = AppThemeStyle.DEFAULT.lightPalette.ctaBackground,
  onCta = AppThemeStyle.DEFAULT.lightPalette.onCta,
  ayahHighlightFill = AppThemeStyle.DEFAULT.lightPalette.ayahHighlightFill,
  editorialAccent = AppThemeStyle.DEFAULT.lightPalette.editorialAccent,
  surfaceTopGlow = AppThemeStyle.DEFAULT.lightPalette.surfaceTopGlow,
  glassFloatBg = AppThemeStyle.DEFAULT.lightPalette.glassFloatBg,
  shadowTint = AppThemeStyle.DEFAULT.lightPalette.shadowTint
)

val darkQuranColors = QuranColors(
  defaultTextColor = Color.White,
  success = Color(0xFF30D158),
  caution = Color(0xFFFFB35C),
  cautionBackground = Color(0xFF2E2210),
  cautionSeparator = Color(0xFF4A3A20),
  toolbarBackground = AppThemeStyle.DEFAULT.darkPalette.toolbarBackground,
  onToolbar = AppThemeStyle.DEFAULT.darkPalette.onToolbar,
  onToolbarSecondary = AppThemeStyle.DEFAULT.darkPalette.onToolbarSecondary,
  surfaceElevated = AppThemeStyle.DEFAULT.darkPalette.surfaceElevated,
  divider = AppThemeStyle.DEFAULT.darkPalette.divider,
  searchHighlight = AppThemeStyle.DEFAULT.darkPalette.searchHighlight,
  isLightToolbar = AppThemeStyle.DEFAULT.darkPalette.isLightToolbar,
  cardSurface = AppThemeStyle.DEFAULT.darkPalette.cardSurface,
  glassBg = AppThemeStyle.DEFAULT.darkPalette.glassBg,
  glassBorder = AppThemeStyle.DEFAULT.darkPalette.glassBorder,
  mushafOverlayBg = AppThemeStyle.DEFAULT.darkPalette.mushafOverlayBg,
  navBg = AppThemeStyle.DEFAULT.darkPalette.navBg,
  navBorder = AppThemeStyle.DEFAULT.darkPalette.navBorder,
  ctaBackground = AppThemeStyle.DEFAULT.darkPalette.ctaBackground,
  onCta = AppThemeStyle.DEFAULT.darkPalette.onCta,
  ayahHighlightFill = AppThemeStyle.DEFAULT.darkPalette.ayahHighlightFill,
  editorialAccent = AppThemeStyle.DEFAULT.darkPalette.editorialAccent,
  surfaceTopGlow = AppThemeStyle.DEFAULT.darkPalette.surfaceTopGlow,
  glassFloatBg = AppThemeStyle.DEFAULT.darkPalette.glassFloatBg,
  shadowTint = AppThemeStyle.DEFAULT.darkPalette.shadowTint
)

internal fun quranColorsFor(
  style: AppThemeStyle,
  palette: AppThemePalette,
  useDarkTheme: Boolean
): QuranColors {
  val base = if (useDarkTheme) darkQuranColors else lightQuranColors
  val isCalm = style != AppThemeStyle.DEFAULT
  val success = when {
    !isCalm -> base.success
    useDarkTheme -> Color(0xFF79B88C)
    else -> Color(0xFF2B6945)
  }
  val caution = when {
    !isCalm -> base.caution
    useDarkTheme -> Color(0xFFD6A86E)
    else -> Color(0xFF8F5720)
  }
  val cautionBackground = when {
    !isCalm -> base.cautionBackground
    useDarkTheme -> Color(0xFF2D251C)
    else -> Color(0xFFF3E9D9)
  }
  val cautionSeparator = when {
    !isCalm -> base.cautionSeparator
    useDarkTheme -> Color(0xFF43372A)
    else -> Color(0xFFDFCEB5)
  }
  return QuranColors(
    defaultTextColor = palette.textPrimary,
    success = success,
    caution = caution,
    cautionBackground = cautionBackground,
    cautionSeparator = cautionSeparator,
    toolbarBackground = palette.toolbarBackground,
    onToolbar = palette.onToolbar,
    onToolbarSecondary = palette.onToolbarSecondary,
    surfaceElevated = palette.surfaceElevated,
    divider = palette.divider,
    searchHighlight = palette.searchHighlight,
    isLightToolbar = palette.isLightToolbar,
    cardSurface = palette.cardSurface,
    glassBg = palette.glassBg,
    glassBorder = palette.glassBorder,
    mushafOverlayBg = palette.mushafOverlayBg,
    navBg = palette.navBg,
    navBorder = palette.navBorder,
    ctaBackground = palette.ctaBackground,
    onCta = palette.onCta,
    ayahHighlightFill = palette.ayahHighlightFill,
    editorialAccent = palette.editorialAccent,
    surfaceTopGlow = palette.surfaceTopGlow,
    glassFloatBg = palette.glassFloatBg,
    shadowTint = palette.shadowTint
  )
}

val LocalQuranColors = staticCompositionLocalOf<QuranColors> {
  error("CompositionLocal QuranColors not present")
}
