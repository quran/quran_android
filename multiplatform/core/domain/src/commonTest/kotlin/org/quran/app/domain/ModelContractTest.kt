package org.quran.app.domain

import org.quran.app.model.*
import kotlin.test.*

class ModelContractTest {
    @Test fun verseIdentifiersRespectChapterBoundaries() {
        assertEquals(7, VerseId(1, 7).ayah)
        assertEquals(286, VerseId(2, 286).ayah)
        assertEquals(6, VerseId(114, 6).ayah)
        for ((chapter, verse) in listOf(0 to 1, 115 to 1, 1 to 0, 1 to 8, 2 to 287, 114 to 7)) {
            assertFailsWith<IllegalArgumentException> { VerseId(chapter, verse) }
        }
    }

    @Test fun languagesDeclareReadingDirection() {
        assertTrue(AppLanguage.ARABIC.isRtl)
        assertEquals("ar", AppLanguage.ARABIC.code)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertEquals("en", AppLanguage.ENGLISH.code)
    }

    @Test fun progressDefaultsAndCopyPreserveIndependentLearningStates() {
        val original = StudyProgress()
        assertEquals(VerseId(1, 1), original.lastRead)
        assertTrue(original.memorized.isEmpty())
        assertTrue(original.bookmarks.isEmpty())
        val changed = original.copy(lastRead = VerseId(2, 10), memorized = setOf(VerseId(1, 1)), language = AppLanguage.ARABIC)
        assertEquals(VerseId(1, 1), original.lastRead)
        assertEquals(VerseId(2, 10), changed.lastRead)
        assertEquals(setOf(VerseId(1, 1)), changed.memorized)
        assertTrue(changed.bookmarks.isEmpty())
    }
}
