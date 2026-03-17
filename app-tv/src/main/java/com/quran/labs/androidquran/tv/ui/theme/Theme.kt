package com.quran.labs.androidquran.tv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
  primary = QuranGreenLight,
  onPrimary = DarkBackground,
  primaryContainer = QuranGreen,
  onPrimaryContainer = QuranGoldLight,
  secondary = QuranGold,
  onSecondary = DarkBackground,
  secondaryContainer = QuranGoldLight,
  onSecondaryContainer = DarkBackground,
  background = DarkBackground,
  onBackground = OnDarkBackground,
  surface = DarkSurface,
  onSurface = OnDarkSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = OnDarkBackground,
)

private val LightColorScheme = lightColorScheme(
  primary = QuranGreen,
  onPrimary = LightBackground,
  primaryContainer = QuranGreenLight,
  onPrimaryContainer = DarkBackground,
  secondary = QuranGoldLight,
  onSecondary = DarkBackground,
  secondaryContainer = QuranGold,
  onSecondaryContainer = DarkBackground,
  background = LightBackground,
  onBackground = OnLightBackground,
  surface = LightSurface,
  onSurface = OnLightSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = OnLightBackground,
)

@Composable
fun QuranTvTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = colorScheme.background.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
