package com.quran.mobile.feature.sync.state

import androidx.compose.runtime.Immutable
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncErrorKind
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Instant

@Immutable
internal data class SyncActivityState(
  val status: Status,
  val lastSynced: Instant?,
  val blockingItems: ImmutableList<HeldSyncItem>
) {
  sealed interface Status {
    data object NeverSynced : Status
    data object Syncing : Status
    data class Retrying(val reason: SyncErrorKind) : Status
    data class Failed(val reason: SyncErrorKind?) : Status
    data class Synced(val at: Instant) : Status
  }
}
