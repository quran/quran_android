package org.quran.app.domain

import org.quran.app.model.VerseId
import kotlin.test.*

class RepeatSessionTest {
    private val first = VerseId(1, 1)
    private val second = VerseId(1, 2)

    @Test fun advancesOnlyAfterConfiguredRepetitions() {
        val session = RepeatSession(listOf(first, second), 2)
        assertEquals(first, session.state().currentVerse)
        assertFalse(session.onRecitationCompleted().complete)
        assertEquals(first, session.state().currentVerse)
        assertEquals(1, session.state().completedRepetitions)
        assertEquals(second, session.onRecitationCompleted().currentVerse)
        assertEquals(0, session.state().completedRepetitions)
        session.onRecitationCompleted()
        assertTrue(session.onRecitationCompleted().complete)
        assertTrue(session.onRecitationCompleted().complete)
    }

    @Test fun loopsUntilLearnerExplicitlyMarksMemorized() {
        val session = RepeatSession(listOf(first, second), 2, repeatUntilMemorized = true)
        repeat(7) { assertEquals(first, session.onRecitationCompleted().currentVerse) }
        assertFalse(session.state().complete)
        assertEquals(second, session.markMemorized().currentVerse)
        assertTrue(session.markMemorized().complete)
        assertTrue(session.markMemorized().complete)
    }

    @Test fun resetRestoresInitialState() {
        val session = RepeatSession(listOf(first), 1)
        assertTrue(session.onRecitationCompleted().complete)
        val reset = session.reset()
        assertEquals(first, reset.currentVerse)
        assertEquals(0, reset.completedRepetitions)
        assertFalse(reset.complete)
    }

    @Test fun rejectsUnusableSessions() {
        assertFailsWith<IllegalArgumentException> { RepeatSession(emptyList(), 1) }
        assertFailsWith<IllegalArgumentException> { RepeatSession(listOf(first), 0) }
        assertFailsWith<IllegalArgumentException> { RepeatSession(listOf(first), -1) }
    }
}
