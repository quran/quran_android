package org.quran.app.memorization

import kotlin.test.*
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RepeatSession
import org.quran.app.model.VerseId

class RepeatQueuePlaybackTest {
    private val first = VerseId(1, 1)
    private val second = VerseId(1, 2)
    private val audio = mapOf(first to "file:///first.mp3", second to "file:///second.mp3")

    @Test fun fixedRepeatsLoadCorrectUriOnlyOnVerseChanges() {
        val player = QueuePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(first, second), 2), player, audioForVerse = audio::get)
        controller.play()
        assertEquals(listOf("file:///first.mp3"), player.loaded)
        player.complete()
        assertEquals(listOf("file:///first.mp3"), player.loaded)
        player.complete()
        assertEquals(second, controller.state.currentVerse)
        assertEquals(listOf("file:///first.mp3", "file:///second.mp3"), player.loaded)
        player.complete()
        player.complete()
        assertTrue(controller.state.complete)
        assertFalse(controller.playing)
        assertEquals(listOf("file:///first.mp3", "file:///first.mp3", "file:///second.mp3", "file:///second.mp3"), player.played)
    }

    @Test fun explicitMemorizedAdvancesTheAudioAndRejectsOldCompletion() {
        val player = QueuePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(first, second), 3, true), player, audioForVerse = audio::get)
        controller.play()
        val old = player.callbacks.last()
        controller.markMemorized()
        assertEquals(second, controller.state.currentVerse)
        controller.play()
        old()
        assertEquals(0, controller.state.completedRepetitions)
        assertEquals(listOf("file:///first.mp3", "file:///second.mp3"), player.loaded)
        controller.markMemorized()
        assertTrue(controller.state.complete)
    }

    @Test fun missingOrUnreadableAudioPausesWithoutCountingTheMissingVerse() {
        listOf(false, true).forEach { throws ->
            val player = QueuePlayer()
            var errors = 0
            val controller = RepeatPlaybackController(RepeatSession(listOf(first, second), 1), player,
                onError = { errors++ }, audioForVerse = { verse -> if (verse == first || throws) audio[verse] else null })
            controller.play()
            player.failLoad = throws
            player.complete()
            assertEquals(second, controller.state.currentVerse)
            assertEquals(0, controller.state.completedRepetitions)
            assertFalse(controller.playing)
            assertFalse(controller.state.complete)
            assertEquals(1, errors)
            assertEquals(1, player.played.size)
        }
    }

    @Test fun pauseAndDisposeRejectStaleCallbacksAndResetReloadsFirstVerse() {
        val player = QueuePlayer()
        val controller = RepeatPlaybackController(RepeatSession(listOf(first, second), 1), player, audioForVerse = audio::get)
        controller.play()
        val stale = player.callbacks.last()
        controller.pause()
        controller.play()
        stale()
        assertEquals(first, controller.state.currentVerse)
        assertEquals(1, player.loaded.size)
        player.complete()
        assertEquals(second, controller.state.currentVerse)
        controller.reset()
        controller.play()
        assertEquals(listOf("file:///first.mp3", "file:///second.mp3", "file:///first.mp3"), player.loaded)
        val disposed = player.callbacks.last()
        controller.dispose()
        disposed()
        assertEquals(first, controller.state.currentVerse)
        assertEquals(0, controller.state.completedRepetitions)
    }

    private class QueuePlayer : AudioPlayer {
        val loaded = mutableListOf<String>()
        val played = mutableListOf<String>()
        val callbacks = mutableListOf<() -> Unit>()
        var failLoad = false
        override fun loadLocal(uri: String) { if (failLoad) error("decoder failure"); loaded += uri }
        override fun play(onCompleted: () -> Unit, onError: (String) -> Unit) { played += loaded.last(); callbacks += onCompleted }
        fun complete() = callbacks.last().invoke()
        override fun pause() = Unit
        override fun clearLocal() = Unit
    override fun release() = Unit
    }
}
