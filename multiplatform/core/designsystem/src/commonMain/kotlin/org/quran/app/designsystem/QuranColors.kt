package org.quran.app.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Semantic reading colors. Brass is decorative; readable text uses on-color roles. */
object QuranColors {
    val Forest = Color(0xFF173E2F)
    val Ivory = Color(0xFFF6F2E9)
    val Brass = Color(0xFFC39B4B)
    val Light = lightColorScheme(
        primary = Forest, onPrimary = Color.White,
        primaryContainer = Color(0xFFD5EBDD), onPrimaryContainer = Color(0xFF102C1F),
        secondary = Color(0xFF73561D), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E4BB), onSecondaryContainer = Color(0xFF362706),
        tertiary = Color(0xFF46584D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD5EBDD), onTertiaryContainer = Color(0xFF102C1F),
        background = Ivory, onBackground = Forest,
        surface = Color.White, onSurface = Forest,
        surfaceVariant = Color(0xFFECE8DF), onSurfaceVariant = Color(0xFF46584D),
        outline = Color(0xFF748276), outlineVariant = Color(0xFFCDD3CA),
        surfaceTint = Forest, scrim = Color.Black,
        surfaceDim = Color(0xFFDDD9D0), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF7F0),
        surfaceContainer = Ivory, surfaceContainerHigh = Color(0xFFECE8DF),
        surfaceContainerHighest = Color(0xFFE6E2D9),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        inverseSurface = Color(0xFF25372B), inverseOnSurface = Color(0xFFE7EEE9),
        inversePrimary = Color(0xFFA9D5BC),
    )
    val Dark = darkColorScheme(
        primary = Color(0xFFA9D5BC), onPrimary = Color(0xFF0D3522),
        primaryContainer = Color(0xFF254D36), onPrimaryContainer = Color(0xFFD5EBDD),
        secondary = Color(0xFFE5C177), onSecondary = Color(0xFF3A2B0F),
        secondaryContainer = Color(0xFF57421B), onSecondaryContainer = Color(0xFFF5E4BB),
        tertiary = Color(0xFFC3D1C6), onTertiary = Color(0xFF23372B),
        tertiaryContainer = Color(0xFF425849), onTertiaryContainer = Color(0xFFE7EEE9),
        background = Color(0xFF0E1914), onBackground = Color(0xFFE7EEE9),
        surface = Color(0xFF16261D), onSurface = Color(0xFFE7EEE9),
        surfaceVariant = Color(0xFF23372B), onSurfaceVariant = Color(0xFFC3D1C6),
        outline = Color(0xFF718779), outlineVariant = Color(0xFF425849),
        surfaceTint = Color(0xFFA9D5BC), scrim = Color.Black,
        surfaceDim = Color(0xFF0E1914), surfaceBright = Color(0xFF33443A),
        surfaceContainerLowest = Color(0xFF09120D), surfaceContainerLow = Color(0xFF122219),
        surfaceContainer = Color(0xFF16261D), surfaceContainerHigh = Color(0xFF203027),
        surfaceContainerHighest = Color(0xFF2B3B31),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE7EEE9), inverseOnSurface = Forest,
        inversePrimary = Forest,
    )
}

// Existing screens retain source compatibility while moving to semantic Material colors.
val Forest = QuranColors.Forest
val Ivory = QuranColors.Ivory
val Brass = QuranColors.Brass
