package org.quran.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.domain.RepeatSession
import org.quran.app.memorization.RecitationQueueController
import org.quran.app.memorization.RepeatPlaybackController
import org.quran.app.model.VerseId
import org.quran.app.reader.ReaderListeningState

/** Reader orchestration reuses validated queues without mutating reading or memorization progress. */
internal class ReaderListeningController(
    repository: RecitationRepository,
    storage: RecitationStorageRepository,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ReaderListeningState())
    val state = mutableState.asStateFlow()
    private var reciterId: String? = null
    private var playback: RepeatPlaybackController? = null
    private var ownsSource = false
    private var generation = 0L
    private var active = true
    private val queue = RecitationQueueController(
        repository, scope, ::audioReady, ::clearPlayback, storage,
    )
    private val observation = scope.launch {
        queue.state.collect { preparation ->
            val verse = state.value.verseId
            if (active && verse != null && preparation.reciterId == reciterId && preparation.verses == listOf(verse)) {
                mutableState.value = state.value.copy(
                    isPreparing = preparation.isDownloading,
                    failed = state.value.failed || preparation.failed,
                )
            }
        }
    }

    fun listen(reciterId: String, verse: VerseId) {
        if (!active || !scope.isActive) return
        generation++
        this.reciterId = reciterId
        mutableState.value = ReaderListeningState(verseId = verse)
        queue.select(reciterId, listOf(verse))
        queue.prepareAudio()
    }

    private fun audioReady(uris: Map<VerseId, String>) {
        val verse = state.value.verseId ?: return
        if (!active) return
        val attempt = generation
        val prepared = uris.toMap()
        mutableState.value = state.value.copy(isReady = true, isPreparing = false, failed = false)
        playback = RepeatPlaybackController(
            RepeatSession(listOf(verse), 1, false), player,
            onChanged = { _, playing ->
                if (active && generation == attempt) mutableState.value = state.value.copy(isPlaying = playing)
            },
            onError = {
                if (active && generation == attempt) mutableState.value = state.value.copy(isPlaying = false, failed = true)
            },
            audioForVerse = { prepared[it] },
        )
        ownsSource = true
        playback?.play()
    }

    fun togglePlayback() {
        if (!active || !state.value.isReady || state.value.failed) return
        playback?.let {
            if (it.playing) it.pause() else {
                if (it.state.complete) it.reset()
                it.play()
            }
        }
    }

    fun retry() {
        val id = reciterId ?: return
        val verse = state.value.verseId ?: return
        listen(id, verse)
    }

    fun stop() {
        if (!active) return
        val id = reciterId
        val verse = state.value.verseId
        generation++
        mutableState.value = ReaderListeningState()
        if (id != null && verse != null) queue.select(id, listOf(verse)) else clearPlayback()
        reciterId = null
    }

    private fun clearPlayback() {
        playback?.dispose()
        playback = null
        if (ownsSource) {
            player.clearLocal()
            ownsSource = false
        }
        if (active) mutableState.value = state.value.copy(isPlaying = false, isReady = false, failed = false)
    }

    fun close() {
        if (!active) return
        generation++
        active = false
        observation.cancel()
        queue.close()
        mutableState.value = ReaderListeningState()
    }
}
