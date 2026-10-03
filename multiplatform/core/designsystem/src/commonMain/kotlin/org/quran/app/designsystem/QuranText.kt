package org.quran.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

enum class QuranTextVariant { Display, Heading, Subheading, Title, Body, Supporting, Label, Caption }

@Composable
fun QuranText(
    text: String,
    modifier: Modifier = Modifier,
    variant: QuranTextVariant = QuranTextVariant.Body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val typography = MaterialTheme.typography
    val style = when (variant) {
        QuranTextVariant.Display -> typography.displayLarge
        QuranTextVariant.Heading -> typography.headlineLarge
        QuranTextVariant.Subheading -> typography.headlineMedium
        QuranTextVariant.Title -> typography.titleLarge
        QuranTextVariant.Body -> typography.bodyLarge
        QuranTextVariant.Supporting -> typography.bodyMedium
        QuranTextVariant.Label -> typography.labelLarge
        QuranTextVariant.Caption -> typography.bodySmall
    }
    Text(text, modifier, color = color, style = style, textAlign = textAlign, maxLines = maxLines, overflow = overflow)
}
