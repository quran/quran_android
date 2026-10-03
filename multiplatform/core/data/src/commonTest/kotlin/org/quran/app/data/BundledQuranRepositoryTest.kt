package org.quran.app.data

import org.quran.app.model.*
import kotlin.test.*

class BundledQuranRepositoryTest {
    @Test fun corpusHasCanonicalChaptersAndEveryVerseExactlyOnce() {
        val repo = BundledQuranRepository()
        val chapters = repo.chapters()
        assertEquals((1..114).toList(), chapters.map { it.number })
        val all = chapters.flatMap { chapter ->
            val verses = repo.verses(chapter.number, AppLanguage.ENGLISH)
            assertEquals(chapter.verseCount, verses.size)
            assertEquals((1..chapter.verseCount).toList(), verses.map { it.id.ayah })
            assertTrue(verses.all { it.id.surah == chapter.number && it.arabic.isNotBlank() && it.source.isNotBlank() })
            verses
        }
        assertEquals(6236, all.size)
        assertEquals(6236, all.map { it.id }.toSet().size)
    }

    @Test fun changingAppLanguageDoesNotChangeSacredArabicText() {
        val repo = BundledQuranRepository()
        for (chapter in repo.chapters()) {
            assertEquals(repo.verses(chapter.number, AppLanguage.ENGLISH).map { it.arabic }, repo.verses(chapter.number, AppLanguage.ARABIC).map { it.arabic })
        }
    }

    @Test fun invalidChapterCannotSilentlyReturnUnrelatedVerses() {
        val repo = BundledQuranRepository()
        assertFailsWith<IllegalArgumentException> { repo.verses(0, AppLanguage.ENGLISH) }
        assertFailsWith<IllegalArgumentException> { repo.verses(115, AppLanguage.ENGLISH) }
    }
}
