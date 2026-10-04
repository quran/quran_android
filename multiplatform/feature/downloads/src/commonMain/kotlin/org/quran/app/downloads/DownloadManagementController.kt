package org.quran.app.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.RecitationDownload
import org.quran.app.model.RecitationRemovalResult

/** UI-scoped operations publish only while their screen and attempt remain current. */
class DownloadManagementController(
    private val storage: RecitationStorageRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(DownloadManagementState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var active = true

    fun refresh() {
        if (!active || state.value.isBusy) return
        perform { storage.inventory().let { DownloadManagementState(snapshot = it.copy(entries = it.entries.toList())) } }
    }

    fun remove(download: RecitationDownload) {
        if (!active || state.value.isBusy) return
        perform(download) {
            val notice = when (storage.removeDownload(download.reciterId, download.verseId)) {
                RecitationRemovalResult.REMOVED -> DownloadManagementNotice.REMOVED
                RecitationRemovalResult.NOT_FOUND -> DownloadManagementNotice.NOT_FOUND
                RecitationRemovalResult.IN_USE -> DownloadManagementNotice.IN_USE
            }
            val snapshot = storage.inventory()
            DownloadManagementState(snapshot = snapshot.copy(entries = snapshot.entries.toList()), notice = notice)
        }
    }

    private fun perform(removing: RecitationDownload? = null, operation: suspend () -> DownloadManagementState) {
        val attempt = ++generation
        mutableState.value = state.value.copy(isLoading = removing == null, removing = removing, notice = null)
        job = scope.launch {
            try {
                val result = operation()
                if (active && attempt == generation) mutableState.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (active && attempt == generation) mutableState.value = state.value.copy(isLoading = false, removing = null, notice = if (removing == null) DownloadManagementNotice.FAILED else DownloadManagementNotice.REMOVE_FAILED)
            }
        }.also { launched ->
            launched.invokeOnCompletion { cause ->
                if (cause is CancellationException && active && attempt == generation) {
                    mutableState.value = state.value.copy(isLoading = false, removing = null)
                }
            }
        }
    }

    fun close() {
        if (!active) return
        active = false
        generation++
        job?.cancel()
        mutableState.value = state.value.copy(isLoading = false, removing = null)
    }
}
