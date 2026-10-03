package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.quran.app.domain.QuranRepository
import org.quran.app.model.VerseId

class CanonicalJuzMetadataTest {
    private val repository: QuranRepository = BundledQuranRepository()

    @Test fun allThirtyPartsHaveUniqueOrderedCanonicalStarts() {
        val juzs = repository.juzs()
        assertEquals((1..30).toList(), juzs.map { it.number })
        assertEquals(30, juzs.map { it.start }.toSet().size)
        assertEquals(VerseId(1, 1), juzs.first().start)
        assertTrue(juzs.zipWithNext().all { (left, right) ->
            left.start.surah < right.start.surah ||
                (left.start.surah == right.start.surah && left.start.ayah < right.start.ayah)
        })
        for (juz in juzs) {
            assertEquals(juz.start, repository.verses(juz.start.surah)[juz.start.ayah - 1].id)
        }
    }

    @Test fun midpointAndFinalPartDoNotFallBackToTheOpeningSurah() {
        assertEquals(VerseId(18, 75), repository.juzs().single { it.number == 16 }.start)
        assertEquals(VerseId(78, 1), repository.juzs().single { it.number == 30 }.start)
    }
}
