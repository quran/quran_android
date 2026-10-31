package com.quran.labs.androidquran.common.ui.core

import android.os.Build
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

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

private fun accentedColors(accent: QuranAccent, useDarkTheme: Boolean): ColorScheme {
  val base = if (useDarkTheme) DarkColors else LightColors
  val primary = if (useDarkTheme) accent.dark else accent.light
  return base.copy(
    primary = primary,
    onPrimary = if (useDarkTheme) accent.onDark else accent.onLight,
    primaryContainer = primary.copy(alpha = 0.12f).compositeOver(base.surface),
    onPrimaryContainer = primary,
    inversePrimary = if (useDarkTheme) accent.light else accent.dark,
    surfaceTint = primary
  )
}

private val forceLtr = listOf("huawei", "lenovo", "tecno")

object QuranIcons {
  val ArrowBack: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ArrowBack
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
  val Logout: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Logout
  val MenuBook: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.MenuBook
  val Mic: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Mic
  val Pause: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Pause
  val Person: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Person
  val PlayArrow: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.PlayArrow
  val Repeat: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Repeat
  val Search: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Search
  val Settings: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Settings
  val Speed: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Speed
  val Stop: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Stop
  val Sync: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Sync
  val Warning: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Warning
}

@Composable
fun QuranTheme(
  useDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val accent = QuranAccent.current
  val colors = remember(accent, useDarkTheme) { accentedColors(accent, useDarkTheme) }

  val quranColors = if (useDarkTheme) {
    darkQuranColors
  } else {
    lightQuranColors
  }

  // hack workaround for https://issuetracker.google.com/issues/266059178
  // crashes on Lollipop / MR1, mostly on Huawei and Lenovo devices due to
  // Compose in RTL. Force LTR for all Composables on Lollipop to attempt
  // to work around this crash for now.
  val locals =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M &&
      Build.MANUFACTURER.lowercase() in forceLtr
    ) {
      arrayOf(LocalLayoutDirection provides LayoutDirection.Ltr)
    } else {
      emptyArray()
    }

  CompositionLocalProvider(*locals) {
    CompositionLocalProvider(LocalQuranColors provides quranColors) {
      MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
      )
    }
  }
}
