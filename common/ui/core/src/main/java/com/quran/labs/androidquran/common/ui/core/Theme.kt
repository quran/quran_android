package com.quran.labs.androidquran.common.ui.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector

private val LightColors = lightColorScheme(
  secondary = lightSecondary,
  onSecondary = lightOnSecondary,
  secondaryContainer = lightSecondaryContainer,
  onSecondaryContainer = lightOnSecondaryContainer,
  tertiary = lightTertiary,
  onTertiary = lightOnTertiary,
  tertiaryContainer = lightTertiaryContainer,
  onTertiaryContainer = lightOnTertiaryContainer,
  error = lightError,
  errorContainer = lightErrorContainer,
  onError = lightOnError,
  onErrorContainer = lightOnErrorContainer,
  background = lightBackground,
  onBackground = lightOnBackground,
  surface = lightSurface,
  onSurface = lightOnSurface,
  surfaceVariant = lightSurfaceVariant,
  onSurfaceVariant = lightOnSurfaceVariant,
  outline = lightOutline,
  inverseOnSurface = lightInverseOnSurface,
  inverseSurface = lightInverseSurface,
  surfaceContainerHighest = lightSurfaceColorHighest
)

private val DarkColors = darkColorScheme(
  secondary = darkSecondary,
  onSecondary = darkOnSecondary,
  secondaryContainer = darkSecondaryContainer,
  onSecondaryContainer = darkOnSecondaryContainer,
  tertiary = darkTertiary,
  onTertiary = darkOnTertiary,
  tertiaryContainer = darkTertiaryContainer,
  onTertiaryContainer = darkOnTertiaryContainer,
  error = darkError,
  errorContainer = darkErrorContainer,
  onError = darkOnError,
  onErrorContainer = darkOnErrorContainer,
  background = darkBackground,
  onBackground = darkOnBackground,
  surface = darkSurface,
  onSurface = darkOnSurface,
  surfaceVariant = darkSurfaceVariant,
  onSurfaceVariant = darkOnSurfaceVariant,
  outline = darkOutline,
  inverseOnSurface = darkInverseOnSurface,
  inverseSurface = darkInverseSurface,
  surfaceContainerHighest = darkSurfaceColorHighest
)

private fun styledColors(
  style: AppThemeStyle,
  accent: QuranAccent,
  useDarkTheme: Boolean
): ColorScheme {
  val base = if (useDarkTheme) DarkColors else LightColors
  val palette = style.palette(useDarkTheme)
  val primary = if (style == AppThemeStyle.DEFAULT) {
    if (useDarkTheme) accent.dark else accent.light
  } else {
    palette.accent
  }
  val onPrimary = if (style == AppThemeStyle.DEFAULT) {
    if (useDarkTheme) accent.onDark else accent.onLight
  } else {
    palette.onAccent
  }
  val inversePrimary = if (style == AppThemeStyle.DEFAULT) {
    if (useDarkTheme) accent.light else accent.dark
  } else {
    if (useDarkTheme) style.lightPalette.accent else style.darkPalette.accent
  }
  return base.copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primary.copy(alpha = 0.14f).compositeOver(palette.surface),
    onPrimaryContainer = primary,
    inversePrimary = inversePrimary,
    secondary = palette.accent,
    onSecondary = palette.onAccent,
    secondaryContainer = palette.headerBackground,
    onSecondaryContainer = palette.onHeader,
    tertiary = palette.tertiary,
    onTertiary = palette.onTertiary,
    tertiaryContainer = primary.copy(alpha = 0.16f).compositeOver(palette.surface),
    onTertiaryContainer = palette.textPrimary,
    background = palette.surface,
    onBackground = palette.textPrimary,
    surface = palette.surface,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surfaceVariant,
    onSurfaceVariant = palette.textSecondary,
    surfaceTint = primary,
    outline = palette.textSecondary,
    outlineVariant = palette.divider,
    surfaceContainerLowest = palette.surface,
    surfaceContainerLow = palette.surfaceElevated,
    surfaceContainer = palette.surfaceElevated,
    surfaceContainerHigh = palette.cardSurface,
    surfaceContainerHighest = palette.cardSurface
  )
}

object QuranIcons {
  val AccountCircle: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.AccountCircle
  val ArrowBack: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ArrowBack
  val ArrowCircleDown: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ArrowCircleDown
  val BookmarkBorder: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.BookmarkBorder
  val Chat: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Chat
  val Check: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Check
  val CheckCircle: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.CheckCircle
  val ChevronRight: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ChevronRight
  val Close: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Close
  val ErrorOutline: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ErrorOutline
  val ExpandMore: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ExpandMore
  val FastForward: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.FastForward
  val FastRewind: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.FastRewind
  val HighlightOff: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.HighlightOff
  val Logout: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Logout
  val MenuBook: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.MenuBook
  val Mic: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Mic
  val Notes: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Notes
  val OpenInNew: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.OpenInNew
  val Pause: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Pause
  val Person: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Person
  val PlayArrow: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.PlayArrow
  val Repeat: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Repeat
  val Search: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Search
  val Settings: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Settings
  val Speed: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Speed
  val Star: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Star
  val Stop: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Stop
  val Sync: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Sync
  val Warning: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Warning
}

@Composable
fun QuranTheme(
  useDarkTheme: Boolean = isSystemInDarkTheme(),
  style: AppThemeStyle = AppThemeStyle.current,
  content: @Composable () -> Unit
) {
  val accent = QuranAccent.current
  val colors = remember(style, accent, useDarkTheme) { styledColors(style, accent, useDarkTheme) }
  val palette = remember(style, useDarkTheme) { style.palette(useDarkTheme) }
  val quranColors = remember(style, palette, useDarkTheme) {
    quranColorsFor(style, palette, useDarkTheme)
  }

  CompositionLocalProvider(LocalQuranColors provides quranColors) {
    MaterialTheme(
      colorScheme = colors,
      typography = AppTypography,
      content = content
    )
  }
}
