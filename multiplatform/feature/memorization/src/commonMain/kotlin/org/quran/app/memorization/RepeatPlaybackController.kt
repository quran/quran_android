package org.quran.app.memorization

import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RepeatSession
import org.quran.app.domain.RepeatState
import org.quran.app.model.VerseId

/** Presentation coordinator. A callback belongs to one play attempt, never to a later session. */
class RepeatPlaybackController(
    private val session: RepeatSession,
    private val player: AudioPlayer,
    private val onChanged: (RepeatState, Boolean) -> Unit = { _, _ -> },
    private val onError: () -> Unit = {},
    private val audioForVerse: ((VerseId) -> String?)? = null,
) {
    var state = session.state()
        private set
    var playing = false
        private set
    private var generation = 0L
    private var active = true
    private var loadedVerse: VerseId? = null

    fun play() {
        if (!active || state.complete || playing) return
        playing = true
        notifyChanged()
        playAttempt()
    }

    private fun playAttempt() {
        val attempt = ++generation
        if (audioForVerse != null && loadedVerse != state.currentVerse) {
            try {
                val uri = audioForVerse.invoke(state.currentVerse)
                if (uri == null) { failAttempt(attempt); return }
                player.loadLocal(uri)
                loadedVerse = state.currentVerse
            } catch (_: Exception) {
                failAttempt(attempt)
                return
            }
        }
        player.play(
            onCompleted = {
                if (active && playing && attempt == generation) {
                    state = session.onRecitationCompleted()
                    if (state.complete) pause() else { notifyChanged(); playAttempt() }
                }
            },
            onError = {
                failAttempt(attempt)
            },
        )
    }

    private fun failAttempt(attempt: Long) {
        if (active && attempt == generation) {
            pause()
            onError()
        }
    }

    fun pause() {
        ++generation
        playing = false
        player.pause()
        notifyChanged()
    }

    fun repeatManually() {
        if (!active || playing || state.complete) return
        state = session.onRecitationCompleted()
        notifyChanged()
    }

    fun markMemorized() {
        if (!active) return
        pause()
        state = session.markMemorized()
        notifyChanged()
    }

    fun reset() {
        if (!active) return
        pause()
        state = session.reset()
        notifyChanged()
    }

    fun dispose() {
        active = false
        pause()
    }

    private fun notifyChanged() = onChanged(state, playing)
}
