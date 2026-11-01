package com.quran.mobile.feature.sync.presenter

import androidx.compose.runtime.BroadcastFrameClock
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.quran.mobile.feature.sync.state.AccountSettingsEvent
import com.quran.mobile.feature.sync.state.AccountSettingsState
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.auth.model.AuthState
import com.quran.shared.auth.model.UserInfo
import com.quran.shared.pipeline.SyncError
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsPresenterTest {
  private val authState = MutableStateFlow<AuthState>(AuthState.Idle)
  private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
  private val observing = MutableStateFlow(true)
  private var syncs = 0
  private val presenter = AccountSettingsPresenter(
    authState,
    SyncActivityPresenter(syncStatus, MutableStateFlow(emptyList()), MutableStateFlow(emptyList())),
    triggerSync = { syncs++ }
  )

  private fun states() = moleculeFlow(RecompositionMode.Immediate) {
    presenter.present(observing)
  }

  @Test fun `signed in only while auth succeeds`() = runTest {
    states().test {
      assertEquals(AccountSettingsState.SignedOut, awaitItem())

      authState.value = AuthState.Success(user)
      runCurrent()
      val signedIn = expectMostRecentItem() as AccountSettingsState.SignedIn
      assertEquals("Aisha Rahman", signedIn.profile.name)
      assertEquals(Status.NeverSynced, signedIn.activity.status)

      authState.value = AuthState.Idle
      runCurrent()
      assertEquals(AccountSettingsState.SignedOut, expectMostRecentItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `a signed-in state without a user keeps the last known profile`() = runTest {
    authState.value = AuthState.Success(user)
    states().test {
      assertEquals("Aisha Rahman", (awaitItem() as AccountSettingsState.SignedIn).profile.name)

      authState.value = AuthState.Success(null)
      runCurrent()
      val signedIn = expectMostRecentItem() as AccountSettingsState.SignedIn
      assertEquals("Aisha Rahman", signedIn.profile.name)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `a signed-in state without any user shows an empty profile`() = runTest {
    authState.value = AuthState.Success(null)
    states().test {
      val signedIn = awaitItem() as AccountSettingsState.SignedIn
      assertNull(signedIn.profile.name)
      assertEquals("", signedIn.profile.initials)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `sync now triggers a sync`() = runTest {
    authState.value = AuthState.Success(user)
    states().test {
      (awaitItem() as AccountSettingsState.SignedIn).eventSink(AccountSettingsEvent.SyncNow)
      assertEquals(1, syncs)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `signing in again while frames are paused starts fresh`() = runTest {
    authState.value = AuthState.Success(user)
    syncStatus.value = retrying()
    val clock = BroadcastFrameClock()
    var latest: AccountSettingsState? = null
    backgroundScope.launch(clock) {
      moleculeFlow(RecompositionMode.ContextClock) { presenter.present(observing) }
        .collect { latest = it }
    }
    runCurrent()
    assertEquals(Status.Retrying(SyncErrorKind.NETWORK), (latest as AccountSettingsState.SignedIn).activity.status)

    // hidden: collection stops and no frames arrive
    observing.value = false
    authState.value = AuthState.Idle
    runCurrent()
    authState.value = AuthState.Success(otherUser)
    syncStatus.value = SyncStatus.Syncing
    runCurrent()
    observing.value = true
    runCurrent()
    clock.sendFrame(0L)
    runCurrent()

    val signedIn = latest as AccountSettingsState.SignedIn
    assertEquals("Omar Farouk", signedIn.profile.name)
    assertEquals(Status.Syncing, signedIn.activity.status)
  }

  @Test fun `another account starts fresh even without a sign-out in between`() = runTest {
    authState.value = AuthState.Success(user)
    syncStatus.value = retrying()
    states().test {
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), (awaitItem() as AccountSettingsState.SignedIn).activity.status)
      runCurrent()

      syncStatus.value = SyncStatus.Syncing
      authState.value = AuthState.Success(otherUser)
      runCurrent()
      val signedIn = expectMostRecentItem() as AccountSettingsState.SignedIn
      assertEquals("Omar Farouk", signedIn.profile.name)
      assertEquals(Status.Syncing, signedIn.activity.status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun retrying() = SyncStatus.Retrying(
    Instant.fromEpochSeconds(100),
    SyncError(SyncErrorKind.NETWORK, null, null, "test"),
    Instant.fromEpochSeconds(102)
  )

  private val user = UserInfo(id = "1", name = "Aisha Rahman", email = "aisha@example.com")
  private val otherUser = UserInfo(id = "2", name = "Omar Farouk", email = "omar@example.com")
}
