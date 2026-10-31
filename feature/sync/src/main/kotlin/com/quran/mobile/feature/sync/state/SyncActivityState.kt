package com.quran.mobile.feature.sync.state

import androidx.compose.runtime.Immutable
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncRun
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

@Immutable
internal data class SyncActivityState(
  val status: Status,
  val lastSynced: Instant?,
  val heldItems: ImmutableList<HeldSyncItem>,
  val runs: ImmutableList<SyncRun> = persistentListOf()
) {
  val blockingItems: ImmutableList<HeldSyncItem> = heldItems.filter { it.waitingOn == null }.toImmutableList()

  sealed interface Status {
    data object NeverSynced : Status
    data object Syncing : Status
    data class Retrying(val reason: SyncErrorKind) : Status
    data class Failed(val reason: SyncErrorKind?) : Status
    data class Synced(val at: Instant) : Status
  }
}
