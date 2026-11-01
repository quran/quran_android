package com.quran.mobile.feature.sync.state

import com.quran.shared.pipeline.SyncRun
import kotlinx.collections.immutable.ImmutableList

internal sealed interface AccountSyncState {
  data object SessionEnded : AccountSyncState

  data class SignedIn(
    val profile: AccountProfile,
    val activity: SyncActivityState,
    val heldRows: ImmutableList<HeldItemRowState>,
    val blockingItemCount: Int,
    val recentRuns: ImmutableList<SyncRun>,
    val isSigningOut: Boolean,
    val signOutError: String?,
    val eventSink: (AccountSyncEvent) -> Unit
  ) : AccountSyncState
}

internal sealed interface AccountSyncEvent {
  data object SyncNow : AccountSyncEvent

  data object ConfirmSignOut : AccountSyncEvent

  data object DismissSignOutError : AccountSyncEvent
}
