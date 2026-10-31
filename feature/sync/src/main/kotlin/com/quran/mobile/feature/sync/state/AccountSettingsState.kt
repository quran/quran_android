package com.quran.mobile.feature.sync.state

import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface AccountSettingsState {
  data object SignedOut : AccountSettingsState

  data class SignedIn(
    val profile: AccountProfile,
    val activity: SyncActivityState,
    val eventSink: (AccountSettingsEvent) -> Unit = {}
  ) : AccountSettingsState
}

internal sealed interface AccountSettingsEvent {
  data object SyncNow : AccountSettingsEvent
}
