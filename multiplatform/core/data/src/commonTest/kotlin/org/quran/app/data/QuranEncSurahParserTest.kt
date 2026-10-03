package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.quran.app.model.VerseId

class QuranEncSurahParserTest {
    private val parser = QuranEncTranslationParser()
    private val edition = TranslationTestFixtures.edition()

    @Test
    fun preservesTranslationAndFootnotesVerbatim() {
        val source = "In the name of Allāh,[2] the Entirely Merciful.\n  "

        val verses = parser.surah(TranslationTestFixtures.surahJson(firstTranslation = source), edition, 1)

        assertEquals(7, verses.size)
        assertEquals(VerseId(1, 1), verses.first().verseId)
        assertEquals(source, verses.first().text)
        assertEquals("[2] note", verses.first().footnotes)
    }

    @Test
    fun rejectsIncompleteOrOutOfOrderVerseNumbering() {
        assertFails { parser.surah(TranslationTestFixtures.surahJson(count = 6), edition, 1) }
        val outOfOrder = TranslationTestFixtures.surahJson().replace("\"aya\":\"2\"", "\"aya\":\"3\"")
        assertFails { parser.surah(outOfOrder, edition, 1) }
    }
}
