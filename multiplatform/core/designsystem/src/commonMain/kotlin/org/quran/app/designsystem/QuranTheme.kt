package org.quran.app.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun QuranTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) QuranColors.Dark else QuranColors.Light,
        typography = QuranTypography,
        shapes = QuranShapes,
        content = content,
    )
}
