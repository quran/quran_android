package org.quran.app.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.quran.app.model.ReadingTextSize

/** SP styles retain platform font scaling and the Arabic text's RTL direction. */
fun quranArabicReadingStyle(textSize: ReadingTextSize, forceLarge: Boolean = false): TextStyle =
    if (forceLarge || textSize == ReadingTextSize.LARGE) QuranLargeArabicTextStyle else QuranArabicTextStyle

fun quranTranslationReadingStyle(
    textSize: ReadingTextSize,
    baseStyle: TextStyle = QuranTypography.bodyLarge,
): TextStyle = when (textSize) {
    ReadingTextSize.DEFAULT -> baseStyle.copy(fontSize = 16.sp, lineHeight = 24.sp)
    ReadingTextSize.LARGE -> baseStyle.copy(fontSize = 20.sp, lineHeight = 30.sp)
}
