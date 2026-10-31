package com.quran.mobile.feature.sync.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.quran.mobile.feature.sync.QuranSyncManager
import com.quran.mobile.feature.sync.state.AccountSettingsEvent
import com.quran.mobile.feature.sync.state.AccountSettingsState
import com.quran.mobile.feature.sync.state.accountProfile
import com.quran.shared.auth.model.AuthState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

internal class AccountSettingsPresenter(
  private val authState: StateFlow<AuthState>,
  private val syncActivityPresenter: SyncActivityPresenter,
  private val triggerSync: () -> Unit
) {
  @Inject
  constructor(syncManager: QuranSyncManager) : this(
    syncManager.authState,
    SyncActivityPresenter(syncManager),
    syncManager::triggerSync
  )

  @Composable
  fun present(observing: Flow<Boolean>): AccountSettingsState {
    // sessions are counted by a collector, so a sign-out while frames are paused still counts
    val auth by remember { authState.withSessions() }
      .collectAsState(initial = remember { AuthSession(authState.value, 0) })
    if (auth.state !is AuthState.Success) return AccountSettingsState.SignedOut
    return key(auth.session) { presentSignedIn(observing) }
  }

  @Composable
  private fun presentSignedIn(observing: Flow<Boolean>): AccountSettingsState.SignedIn {
    // a signed-in state without a user keeps the last known profile
    val profile by remember {
      authState
        .mapNotNull { (it as? AuthState.Success)?.userInfo }
        .map(::accountProfile)
    }.collectAsState(
      initial = remember { accountProfile((authState.value as? AuthState.Success)?.userInfo) }
    )
    val activity = syncActivityPresenter.present(observing)

    val eventSink: (AccountSettingsEvent) -> Unit = { event ->
      when (event) {
        AccountSettingsEvent.SyncNow -> triggerSync()
      }
    }
    return AccountSettingsState.SignedIn(profile, activity, eventSink)
  }
}

private data class AuthSession(val state: AuthState, val session: Int)

// a new sign-in, or another account, starts a new session
private fun Flow<AuthState>.withSessions(): Flow<AuthSession> = flow {
  var session = 0
  var wasSignedIn: Boolean? = null
  var userId: String? = null
  collect { state ->
    val isSignedIn = state is AuthState.Success
    val id = (state as? AuthState.Success)?.userInfo?.id
    if ((isSignedIn && wasSignedIn == false) || (id != null && userId != null && id != userId)) {
      session++
    }
    wasSignedIn = isSignedIn
    if (id != null) userId = id
    emit(AuthSession(state, session))
  }
}
