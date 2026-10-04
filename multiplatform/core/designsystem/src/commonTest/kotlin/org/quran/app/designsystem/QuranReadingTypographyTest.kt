package org.quran.app.designsystem

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import kotlin.test.*
import org.quran.app.model.ReadingTextSize

class QuranReadingTypographyTest {
    @Test fun arabicDefaultAndLargeRetainRtlAndComfortableLineHeight() {
        val normal = quranArabicReadingStyle(ReadingTextSize.DEFAULT)
        val large = quranArabicReadingStyle(ReadingTextSize.LARGE)
        assertEquals(28.sp, normal.fontSize)
        assertEquals(48.sp, normal.lineHeight)
        assertEquals(34.sp, large.fontSize)
        assertEquals(56.sp, large.lineHeight)
        assertEquals(TextDirection.Rtl, normal.textDirection)
        assertEquals(TextDirection.Rtl, large.textDirection)
        assertEquals(large, quranArabicReadingStyle(ReadingTextSize.DEFAULT, forceLarge = true))
    }

    @Test fun translationSizeIsIndependentAndUsesSpForSystemScaling() {
        val normal = quranTranslationReadingStyle(ReadingTextSize.DEFAULT)
        val large = quranTranslationReadingStyle(ReadingTextSize.LARGE)
        assertEquals(16.sp, normal.fontSize)
        assertEquals(24.sp, normal.lineHeight)
        assertEquals(20.sp, large.fontSize)
        assertEquals(30.sp, large.lineHeight)
    }
}
