package com.quran.mobile.feature.sync.presenter

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.pipeline.SyncError
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncRunTrigger
import com.quran.shared.pipeline.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SyncActivityPresenterTest {
  private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
  private val syncHistory = MutableStateFlow<List<SyncRun>>(emptyList())
  private val presenter = SyncActivityPresenter(
    syncStatus,
    syncHistory,
    MutableStateFlow(emptyList())
  )
  private val observing = MutableStateFlow(true)

  private fun states() = moleculeFlow(RecompositionMode.Immediate) {
    presenter.present(observing)
  }

  @Test fun `retry stays visible through attempts and updates its reason until final failure`() = runTest {
    states().test {
      assertEquals(Status.NeverSynced, awaitItem().status)

      syncStatus.value = retrying(SyncErrorKind.NETWORK)
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), awaitItem().status)
      syncStatus.value = SyncStatus.Syncing
      runCurrent()
      expectNoEvents()

      syncStatus.value = retrying(SyncErrorKind.SERVER)
      assertEquals(Status.Retrying(SyncErrorKind.SERVER), awaitItem().status)
      syncStatus.value = SyncStatus.Syncing
      runCurrent()
      expectNoEvents()

      syncStatus.value = SyncStatus.Failed(time(300), error(SyncErrorKind.SERVER))
      assertEquals(Status.Failed(SyncErrorKind.SERVER), awaitItem().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `success ends an existing retry and a later run can show syncing`() = runTest {
    syncStatus.value = retrying(SyncErrorKind.NETWORK)
    states().test {
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), awaitItem().status)
      syncStatus.value = SyncStatus.Syncing
      runCurrent()
      expectNoEvents()

      syncStatus.value = SyncStatus.Succeeded(time(300))
      assertEquals(Status.Synced(time(300)), awaitItem().status)
      syncStatus.value = SyncStatus.Syncing
      assertEquals(Status.Syncing, awaitItem().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `pausing keeps the last state and resuming catches up`() = runTest {
    states().test {
      assertEquals(Status.NeverSynced, awaitItem().status)
      runCurrent()

      observing.value = false
      runCurrent()
      syncStatus.value = retrying(SyncErrorKind.NETWORK)
      syncHistory.value = listOf(run(SyncRunResult.SUCCEEDED, 200))
      runCurrent()
      expectNoEvents()

      observing.value = true
      runCurrent()
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), expectMostRecentItem().status)
      syncStatus.value = SyncStatus.Idle
      runCurrent()
      assertEquals(Status.Synced(time(200)), expectMostRecentItem().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `a retry that ended while paused gives way to the next sync`() = runTest {
    syncStatus.value = retrying(SyncErrorKind.NETWORK)
    syncHistory.value = listOf(run(SyncRunResult.SUCCEEDED, 50))
    states().test {
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), awaitItem().status)
      runCurrent()
      expectMostRecentItem()

      observing.value = false
      runCurrent()
      syncHistory.value = listOf(run(SyncRunResult.FAILED, 150), run(SyncRunResult.SUCCEEDED, 50))
      syncStatus.value = SyncStatus.Syncing
      runCurrent()
      expectNoEvents()

      observing.value = true
      runCurrent()
      assertEquals(Status.Syncing, expectMostRecentItem().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `a retry still running after a pause stays visible`() = runTest {
    syncStatus.value = retrying(SyncErrorKind.NETWORK)
    syncHistory.value = listOf(run(SyncRunResult.SUCCEEDED, 50))
    states().test {
      assertEquals(Status.Retrying(SyncErrorKind.NETWORK), awaitItem().status)
      runCurrent()
      expectMostRecentItem()

      observing.value = false
      runCurrent()
      syncStatus.value = SyncStatus.Syncing
      runCurrent()
      expectNoEvents()

      observing.value = true
      runCurrent()
      expectNoEvents()

      syncStatus.value = SyncStatus.Failed(time(300), error(SyncErrorKind.NETWORK))
      assertEquals(Status.Failed(SyncErrorKind.NETWORK), awaitItem().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun run(result: SyncRunResult, endedAt: Long) = SyncRun(
    startedAt = time(endedAt - 1),
    endedAt = time(endedAt),
    trigger = SyncRunTrigger.MANUAL,
    result = result,
    attempts = 1,
    pushed = 0,
    pulled = 0,
    held = 0,
    error = null
  )

  private fun time(seconds: Long) = Instant.fromEpochSeconds(seconds)

  private fun error(kind: SyncErrorKind) = SyncError(kind, null, null, "test")

  private fun retrying(kind: SyncErrorKind) =
    SyncStatus.Retrying(time(100), error(kind), time(102))
}
