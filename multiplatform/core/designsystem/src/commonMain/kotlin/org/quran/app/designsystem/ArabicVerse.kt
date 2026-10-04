package org.quran.app.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.quran.app.model.ReadingTextSize

/** The original Arabic text remains untouched regardless of interface locale. */
@Composable
fun ArabicVerse(
    text: String,
    large: Boolean = false,
    modifier: Modifier = Modifier,
    textSize: ReadingTextSize = ReadingTextSize.DEFAULT,
) {
    Text(text, modifier.fillMaxWidth(), style = quranArabicReadingStyle(textSize, forceLarge = large), textAlign = TextAlign.Right)
}
