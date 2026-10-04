package org.quran.app.memorization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.quran.app.domain.RecitationLease
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.VerseId

/** Publishes complete queues only; every attempt belongs to one source and selection. */
class RecitationQueueController(
    private val repository: RecitationRepository,
    private val scope: CoroutineScope,
    private val onAudioReady: (Map<VerseId, String>) -> Unit,
    private val onAudioCleared: () -> Unit = {},
    private val storage: RecitationStorageRepository? = null,
) {
    private val mutableState = MutableStateFlow(RecitationQueueState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var active = true
    private var publishedLease: RecitationLease? = null

    fun select(id: String, verses: List<VerseId>) {
        if (!active) return
        require(id.isNotBlank()) { "Select a reciter" }
        require(verses.size in 1..20 && verses.distinct().size == verses.size) { "Select between one and twenty distinct verses" }
        generation++
        job?.cancel()
        clearPublishedAudio()
        val selected = verses.toList()
        mutableState.value = RecitationQueueState(id, selected, selected.count { repository.cached(id, it) != null })
    }

    fun prepareAudio() {
        val selected = state.value.copy(completedDownloads = 0, failed = false)
        val id = selected.reciterId ?: return
        if (!active || selected.isDownloading) return
        clearPublishedAudio()
        val attempt = ++generation
        mutableState.value = selected.copy(completedDownloads = 0, isDownloading = true, failed = false)
        job = scope.launch {
            val prepared = linkedMapOf<VerseId, String>()
            val available = mutableSetOf<VerseId>()
            var attemptLease: RecitationLease? = null
            try {
                attemptLease = storage?.protect(id, selected.verses.toList())
                currentCoroutineContext().ensureActive()
                if (!active || attempt != generation) return@launch
                available += selected.verses.filter { repository.cached(id, it) != null }
                for (verse in selected.verses) {
                    currentCoroutineContext().ensureActive()
                    val uri = repository.download(id, verse)
                    if (!active || attempt != generation) return@launch
                    prepared[verse] = uri
                    available += verse
                    mutableState.value = selected.copy(
                        cachedCount = available.size,
                        completedDownloads = prepared.size,
                        isDownloading = true,
                    )
                }
                currentCoroutineContext().ensureActive()
                if (active && attempt == generation) {
                    onAudioReady(prepared.toMap())
                    if (active && attempt == generation) {
                        publishedLease = attemptLease
                        attemptLease = null
                        mutableState.value = selected.copy(
                            cachedCount = available.size,
                            completedDownloads = prepared.size,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (active && attempt == generation) mutableState.value = state.value.copy(isDownloading = false, failed = true)
            } finally {
                attemptLease?.let { withContext(NonCancellable) { it.release() } }
            }
        }.also { launched ->
            // Also covers a scope already canceled before the launch body can start.
            launched.invokeOnCompletion { cause ->
                if (cause is CancellationException && active && attempt == generation) {
                    mutableState.value = state.value.copy(isDownloading = false)
                }
            }
        }
    }

    fun close() {
        if (!active) return
        active = false
        generation++
        job?.cancel()
        mutableState.value = state.value.copy(isDownloading = false)
        clearPublishedAudio()
    }

    /** Caller has stopped and removed the old native source before releasing this lease. */
    fun releasePublishedAudioAfterSourceReplacement() {
        releasePublishedLease()
    }

    suspend fun releasePublishedAudioAfterSourceReplacementAndWait() {
        val lease = publishedLease ?: return
        publishedLease = null
        withContext(NonCancellable) { lease.release() }
    }

    /** The player drops its URI before storage is allowed to remove the protected file. */
    private fun clearPublishedAudio() {
        onAudioCleared()
        releasePublishedLease()
    }

    private fun releasePublishedLease() {
        publishedLease?.let { lease ->
            publishedLease = null
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) { lease.release() }
            }
        }
    }
}
