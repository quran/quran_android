package com.quran.labs.androidquran.common.ui.core

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
  primary = lightPrimary,
  onPrimary = lightOnPrimary,
  primaryContainer = lightPrimaryContainer,
  onPrimaryContainer = lightOnPrimaryContainer,
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
  inversePrimary = lightInversePrimary,
  surfaceTint = lightSurfaceTint,
  surfaceContainerHighest = lightSurfaceColorHighest
)

private val DarkColors = darkColorScheme(
  primary = darkPrimary,
  onPrimary = darkOnPrimary,
  primaryContainer = darkPrimaryContainer,
  onPrimaryContainer = darkOnPrimaryContainer,
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
  inversePrimary = darkInversePrimary,
  surfaceTint = darkSurfaceTint,
  surfaceContainerHighest = darkSurfaceColorHighest
)

private val forceLtr = listOf("huawei", "lenovo", "tecno")

object QuranIcons {
  val ArrowBack: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ArrowBack
  val Chat: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Chat
  val Check: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Check
  val Close: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Close
  val ExpandMore: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.ExpandMore
  val FastForward: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.FastForward
  val FastRewind: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.FastRewind
  val MenuBook: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.MenuBook
  val Mic: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Mic
  val Pause: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Pause
  val PlayArrow: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.PlayArrow
  val Repeat: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Repeat
  val Settings: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Settings
  val Speed: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Speed
  val Stop: ImageVector get() = com.quran.labs.androidquran.common.ui.core.icons.Stop
}

@Composable
fun QuranTheme(
  useDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colors = if (!useDarkTheme) {
    LightColors
  } else {
    DarkColors
  }

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
