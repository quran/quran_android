package com.quran.mobile.feature.sync.presenter

import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncError
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncResourceType
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncRunTrigger
import com.quran.shared.pipeline.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class SyncActivityStateTest {
  @Test fun `no completed runs means never synced`() {
    assertEquals(Status.NeverSynced, syncActivityState().status)
    val activity = syncActivityState(runs = listOf(run(SyncRunResult.CANCELLED, 100)))
    assertEquals(Status.NeverSynced, activity.status)
    assertNull(activity.lastSynced)
  }

  @Test fun `active sync and retry take precedence over history`() {
    val history = listOf(run(SyncRunResult.SUCCEEDED, 200))

    assertEquals(Status.Syncing, syncActivityState(status = SyncStatus.Syncing, runs = history).status)
    assertEquals(
      Status.Retrying(SyncErrorKind.NETWORK),
      syncActivityState(status = retrying(), runs = history).status
    )
  }

  @Test fun `idle restores last success from history and ignores cancelled runs`() {
    val activity = syncActivityState(runs = listOf(
      run(SyncRunResult.CANCELLED, 300),
      run(SyncRunResult.SUCCEEDED, 200),
      run(SyncRunResult.SUCCEEDED, 100)
    ))

    assertEquals(Status.Synced(time(200)), activity.status)
    assertEquals(time(200), activity.lastSynced)
  }

  @Test fun `success status wins over failed history at the same or an earlier time`() {
    val history = listOf(run(SyncRunResult.FAILED, 200, SyncErrorKind.NETWORK))
    for (at in listOf(200L, 300L)) {
      val activity = syncActivityState(status = SyncStatus.Succeeded(time(at)), runs = history)
      assertEquals(Status.Synced(time(at)), activity.status)
      assertEquals(time(at), activity.lastSynced)
    }
    assertEquals(
      Status.Synced(time(300)),
      syncActivityState(status = SyncStatus.Succeeded(time(300))).status
    )
  }

  @Test fun `failure status wins over successful history at the same or an earlier time`() {
    val history = listOf(run(SyncRunResult.SUCCEEDED, 200))
    for (at in listOf(200L, 300L)) {
      val activity = syncActivityState(status = failed(at), runs = history)
      assertEquals(Status.Failed(SyncErrorKind.NETWORK), activity.status)
      assertEquals(time(200), activity.lastSynced)
    }
    assertEquals(Status.Failed(SyncErrorKind.NETWORK), syncActivityState(status = failed(300)).status)
  }

  @Test fun `newer failed history wins over stale success status`() {
    val activity = syncActivityState(
      status = SyncStatus.Succeeded(time(100)),
      runs = listOf(run(SyncRunResult.FAILED, 200, SyncErrorKind.SERVER))
    )

    assertEquals(Status.Failed(SyncErrorKind.SERVER), activity.status)
    assertEquals(time(100), activity.lastSynced)
  }

  @Test fun `newer successful history wins over stale status`() {
    val history = listOf(run(SyncRunResult.SUCCEEDED, 200))
    for (status in listOf(failed(100), SyncStatus.Succeeded(time(100)))) {
      val activity = syncActivityState(status = status, runs = history)
      assertEquals(Status.Synced(time(200)), activity.status)
      assertEquals(time(200), activity.lastSynced)
    }
  }

  @Test fun `pulled only keeps the latest failure reason and does not advance last success`() {
    val activity = syncActivityState(runs = listOf(
      run(SyncRunResult.PULLED_ONLY, 400),
      run(SyncRunResult.FAILED, 300, SyncErrorKind.AUTHENTICATION),
      run(SyncRunResult.FAILED, 200, SyncErrorKind.NETWORK),
      run(SyncRunResult.SUCCEEDED, 100)
    ))

    assertEquals(Status.Failed(SyncErrorKind.AUTHENTICATION), activity.status)
    assertEquals(time(100), activity.lastSynced)
  }

  @Test fun `missing error means unknown for failure but no reason for pulled only`() {
    assertEquals(
      Status.Failed(SyncErrorKind.UNKNOWN),
      syncActivityState(runs = listOf(run(SyncRunResult.FAILED, 100))).status
    )
    assertEquals(
      Status.Failed(null),
      syncActivityState(runs = listOf(run(SyncRunResult.PULLED_ONLY, 100))).status
    )
  }

  @Test fun `blocking items exclude dependents even while syncing or failed`() {
    val collection = HeldSyncItem(
      type = SyncResourceType.COLLECTION,
      id = "collection",
      collectionId = null,
      reason = HeldReason.RESERVED_NAME,
      waitingOn = null,
      httpStatus = null,
      serverCode = null
    )
    val dependent = collection.copy(
      type = SyncResourceType.COLLECTION_BOOKMARK,
      id = "2:255",
      collectionId = collection.id,
      reason = HeldReason.WAITING_ON_PARENT,
      waitingOn = collection
    )
    val held = listOf(collection, dependent)
    for (status in listOf(SyncStatus.Idle, SyncStatus.Syncing, failed(300))) {
      assertEquals(listOf(collection), syncActivityState(status = status, heldItems = held).blockingItems)
    }
    assertEquals(
      emptyList<HeldSyncItem>(),
      syncActivityState(heldItems = listOf(dependent)).blockingItems
    )
  }

  private fun time(seconds: Long) = Instant.fromEpochSeconds(seconds)

  private fun error(kind: SyncErrorKind) = SyncError(kind, null, null, "test")

  private fun failed(at: Long, kind: SyncErrorKind = SyncErrorKind.NETWORK) =
    SyncStatus.Failed(time(at), error(kind))

  private fun retrying(kind: SyncErrorKind = SyncErrorKind.NETWORK) =
    SyncStatus.Retrying(time(100), error(kind), time(102))

  private fun run(result: SyncRunResult, endedAt: Long, kind: SyncErrorKind? = null) = SyncRun(
    startedAt = time(endedAt - 5),
    endedAt = time(endedAt),
    trigger = SyncRunTrigger.MANUAL,
    result = result,
    attempts = 1,
    pushed = 0,
    pulled = 0,
    held = 0,
    error = kind?.let(::error)
  )
}
