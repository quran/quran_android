package org.quran.app.memorization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.quran.app.domain.RecitationRepository
import org.quran.app.model.VerseId

/** A download belongs to one selected verse/reciter, never to a later screen or selection. */
class RecitationAudioController(
    private val repository: RecitationRepository,
    private val scope: CoroutineScope,
    private val onAudioReady: (String) -> Unit,
    private val onAudioCleared: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow(RecitationAudioState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var active = true

    fun select(reciterId: String, verseId: VerseId) {
        if (!active) return
        generation++
        job?.cancel()
        onAudioCleared()
        mutableState.value = RecitationAudioState(reciterId, verseId, repository.cached(reciterId, verseId))
    }

    fun prepareAudio() {
        val selected = state.value
        val reciterId = selected.reciterId ?: return
        val verseId = selected.verseId ?: return
        if (!active || selected.isDownloading) return
        val attempt = ++generation
        mutableState.value = selected.copy(isDownloading = true, failed = false)
        job = scope.launch {
            try {
                val uri = repository.download(reciterId, verseId)
                if (active && attempt == generation) {
                    onAudioReady(uri)
                    mutableState.value = selected.copy(cachedUri = uri)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (active && attempt == generation) {
                    mutableState.value = selected.copy(failed = true)
                }
            }
        }
    }

    fun close() {
        active = false
        generation++
        job?.cancel()
    }
}
