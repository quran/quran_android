package org.quran.app.memorization

import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RepeatSession
import org.quran.app.model.VerseId
import kotlin.test.*

class RepeatPlaybackControllerTest {
    private class FakePlayer : AudioPlayer {
        val completions = mutableListOf<() -> Unit>()
        var pauseCount = 0
        override fun loadLocal(uri: String) = Unit
        override fun play(onCompleted: () -> Unit, onError: (String) -> Unit) { completions += onCompleted }
        override fun pause() { pauseCount++ }
        override fun clearLocal() = Unit
        override fun release() = Unit
    }

    private val verse = VerseId(1, 1)

    @Test fun staleCompletionAfterPauseAndResumeCannotAdvanceTheSession() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 2), player)
        controller.play()
        val oldCompletion = player.completions.single()
        controller.pause()
        controller.play()
        oldCompletion()
        assertEquals(0, controller.state.completedRepetitions)
        assertEquals(2, player.completions.size)
        player.completions.last()()
        assertEquals(1, controller.state.completedRepetitions)
        assertEquals(3, player.completions.size)
    }

    @Test fun memorizedStopsPlaybackAndRejectsPendingCompletion() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 3, true), player)
        controller.play()
        val completion = player.completions.single()
        controller.markMemorized()
        assertTrue(controller.state.complete)
        assertFalse(controller.playing)
        completion()
        assertEquals(0, controller.state.completedRepetitions)
        assertEquals(1, player.completions.size)
        assertTrue(player.pauseCount > 0)
    }

    @Test fun resetAndDisposeRejectQueuedCompletions() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 3), player)
        controller.play()
        val completion = player.completions.single()
        controller.reset()
        completion()
        assertEquals(0, controller.state.completedRepetitions)
        controller.play()
        val afterReset = player.completions.last()
        controller.dispose()
        afterReset()
        controller.play()
        assertEquals(0, controller.state.completedRepetitions)
        assertEquals(2, player.completions.size)
    }

    @Test fun manualRepetitionCannotDoubleCountPlaybackAndFixedCountStops() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 1), player)
        controller.play()
        controller.repeatManually()
        assertEquals(0, controller.state.completedRepetitions)
        player.completions.single()()
        assertTrue(controller.state.complete)
        assertFalse(controller.playing)
        controller.markMemorized()
        assertTrue(controller.state.complete)
        assertEquals(1, player.completions.size)
    }
}
