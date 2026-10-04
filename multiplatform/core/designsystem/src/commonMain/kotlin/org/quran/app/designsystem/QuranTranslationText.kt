package org.quran.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.quran.app.model.ReadingTextSize

@Composable
fun QuranTranslationText(
    text: String,
    textSize: ReadingTextSize = ReadingTextSize.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, style = quranTranslationReadingStyle(textSize, MaterialTheme.typography.bodyLarge))
}
