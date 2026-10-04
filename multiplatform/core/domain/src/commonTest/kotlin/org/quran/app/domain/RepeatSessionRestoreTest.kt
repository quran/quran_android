package org.quran.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import org.quran.app.model.VerseId

class RepeatSessionRestoreTest {
    @Test
    fun restoresMidRangeAndRepetitionWithoutPlaying() {
        val verses = listOf(VerseId(2, 3), VerseId(2, 4), VerseId(2, 5))
        val session = RepeatSession(verses, repetitionsPerVerse = 3, repeatUntilMemorized = true)

        val state = session.restore(VerseId(2, 4), completedRepetitions = 2, complete = false)

        assertEquals(VerseId(2, 4), state.currentVerse)
        assertEquals(2, state.completedRepetitions)
        assertEquals(false, state.complete)
    }
}
