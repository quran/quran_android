package com.quran.mobile.feature.sync.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quran.mobile.feature.sync.QuranSyncManager
import com.quran.mobile.feature.sync.state.SyncActivityState
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncStatus
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal class SyncActivityPresenter(
  private val syncStatus: StateFlow<SyncStatus>,
  private val syncHistory: Flow<List<SyncRun>>,
  private val heldItems: Flow<List<HeldSyncItem>>
) {
  @Inject
  constructor(syncManager: QuranSyncManager) : this(
    syncManager.quranDataService.syncStatus,
    syncManager.quranDataService.syncHistory,
    syncManager.quranDataService.heldItems
  )

  @Composable
  fun present(): SyncActivityState {
    val status = remember { mutableStateOf(syncStatus.value) }
    LaunchedEffect(Unit) {
      syncStatus.collect { next ->
        // keep the failure visible between retry attempts instead of flashing back to "Syncing".
        if (status.value !is SyncStatus.Retrying || next !is SyncStatus.Syncing) {
          status.value = next
        }
      }
    }
    val runs by syncHistory.collectAsState(emptyList())
    val held by heldItems.collectAsState(emptyList())
    return syncActivityState(status.value, runs, held)
  }
}

internal fun syncActivityState(
  status: SyncStatus = SyncStatus.Idle,
  runs: List<SyncRun> = emptyList(),
  heldItems: List<HeldSyncItem> = emptyList()
): SyncActivityState {
  // the SDK supplies history newest first
  val lastSuccess = runs.firstOrNull { it.result == SyncRunResult.SUCCEEDED }?.endedAt
  val lastSynced = if (status is SyncStatus.Succeeded) {
    maxOf(status.at, lastSuccess ?: status.at)
  } else {
    lastSuccess
  }
  val latestRun = runs.firstOrNull { it.result != SyncRunResult.CANCELLED }
  val resolvedStatus = when {
    status is SyncStatus.Syncing -> Status.Syncing
    status is SyncStatus.Retrying -> Status.Retrying(status.error.kind)
    status is SyncStatus.Succeeded && (latestRun == null || latestRun.endedAt <= status.at) ->
      Status.Synced(lastSynced ?: status.at)
    status is SyncStatus.Failed && (latestRun == null || latestRun.endedAt <= status.at) ->
      Status.Failed(status.error.kind)
    latestRun?.result == SyncRunResult.FAILED ->
      Status.Failed(latestRun.error?.kind ?: SyncErrorKind.UNKNOWN)
    latestRun?.result == SyncRunResult.PULLED_ONLY ->
      Status.Failed(runs.firstOrNull { it.result == SyncRunResult.FAILED }?.error?.kind)
    lastSynced != null -> Status.Synced(lastSynced)
    else -> Status.NeverSynced
  }
  return SyncActivityState(
    status = resolvedStatus,
    lastSynced = lastSynced,
    blockingItems = heldItems.filter { it.waitingOn == null }.toImmutableList()
  )
}
