package org.quran.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReadingPreferencesTest {
    @Test fun childrenModeUsesLargeTextForArabicAndTranslation() {
        val preferences = ReadingPreferences()

        assertEquals(
            ReadingPreferences(ReadingTextSize.LARGE, ReadingTextSize.LARGE),
            preferences.forChildrenMode(enabled = true),
        )
    }

    @Test fun adultModePreservesTheChosenReadingSizes() {
        val preferences = ReadingPreferences(ReadingTextSize.DEFAULT, ReadingTextSize.LARGE)

        assertSame(preferences, preferences.forChildrenMode(enabled = false))
    }
}
