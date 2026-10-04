package org.quran.app.memorization

import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RepeatSession
import org.quran.app.model.VerseId
import kotlin.test.*

class RepeatPlaybackControllerTest {
    private class FakePlayer : AudioPlayer {
        val completions = mutableListOf<() -> Unit>()
        val playbackChanges = mutableListOf<(Boolean) -> Unit>()
        var pauseCount = 0
        override fun loadLocal(uri: String) = Unit
        override fun play(onCompleted: () -> Unit, onError: (String) -> Unit, onPlaybackChanged: (Boolean) -> Unit) {
            completions += onCompleted
            playbackChanges += onPlaybackChanged
        }
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

    @Test fun externalPauseAndResumeUpdateReaderPlaybackWithoutInvalidatingCompletion() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 1), player)
        controller.play()

        player.playbackChanges.single()(false)
        assertFalse(controller.playing)
        assertEquals(0, controller.state.completedRepetitions)

        player.playbackChanges.single()(true)
        assertTrue(controller.playing)
        player.completions.single()()

        assertTrue(controller.state.complete)
        assertFalse(controller.playing)
    }

    @Test fun queuedExternalResumeAfterUserPauseCannotRestartControllerState() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(verse), 2), player)
        controller.play()
        val queuedPlaybackChange = player.playbackChanges.single()

        controller.pause()
        queuedPlaybackChange(true)

        assertFalse(controller.playing)
        assertEquals(0, controller.state.completedRepetitions)
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

    @Test fun manualAdvanceInvalidatesTheInterruptedVerseCompletion() {
        val player = FakePlayer()
        val controller = RepeatPlaybackController(
            RepeatSession(listOf(VerseId(1, 1), VerseId(1, 2)), 1),
            player,
        )
        controller.play()
        val firstCompletion = player.completions.single()
        player.playbackChanges.single()(false)
        controller.repeatManually()

        assertEquals(VerseId(1, 2), controller.state.currentVerse)
        firstCompletion()
        assertEquals(VerseId(1, 2), controller.state.currentVerse)
        assertEquals(0, controller.state.completedRepetitions)

        player.playbackChanges.last()(true)
        assertFalse(controller.playing)
    }
}
