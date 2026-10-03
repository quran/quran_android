package org.quran.app.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/** The original Arabic text remains untouched regardless of interface locale. */
@Composable
fun ArabicVerse(text: String, large: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), style = if (large) QuranLargeArabicTextStyle else QuranArabicTextStyle, textAlign = TextAlign.Right)
}
