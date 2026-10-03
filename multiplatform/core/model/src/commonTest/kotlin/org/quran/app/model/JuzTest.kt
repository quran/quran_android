package org.quran.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JuzTest {
    @Test fun retainsItsCanonicalStartingAyah() {
        val juz = Juz(16, VerseId(18, 75))
        assertEquals(16, juz.number)
        assertEquals(VerseId(18, 75), juz.start)
    }

    @Test fun rejectsNumbersOutsideTheThirtyQuranParts() {
        for (number in listOf(Int.MIN_VALUE, 0, 31, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { Juz(number, VerseId(1, 1)) }
        }
    }
}
