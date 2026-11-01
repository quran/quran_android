package com.quran.mobile.feature.sync.presenter

import com.quran.mobile.feature.sync.state.AccountSyncEvent
import com.quran.mobile.feature.sync.state.AccountSyncState
import com.quran.mobile.feature.sync.state.HeldItemRowState.Title
import com.quran.mobile.feature.sync.state.HeldItemRowState.Waiting
import com.quran.mobile.feature.sync.state.SignOutWarning
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.auth.model.AuthState
import com.quran.shared.auth.model.UserInfo
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.model.EmptyReadingBookmark
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.NoteRange
import com.quran.shared.persistence.model.ReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.util.toPlatform
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncResourceType
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncRunTrigger
import com.quran.shared.pipeline.SyncStatus
import com.quran.shared.pipeline.UnsyncedItemCounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSyncPresenterTest {
  private val user = UserInfo(id = "1", name = "Aisha Rahman", email = "aisha@example.com")
  private val auth = MutableStateFlow<AuthState>(AuthState.Success(user))
  private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
  private val history = MutableStateFlow<List<SyncRun>>(emptyList())
  private val held = MutableStateFlow<List<HeldSyncItem>>(emptyList())
  private val notes = MutableStateFlow<List<Note>>(emptyList())
  private val collections = MutableStateFlow<List<CollectionWithAyahBookmarks>>(emptyList())
  private val bookmarks = MutableStateFlow<List<ReadingBookmark>>(emptyList())
  private val observing = MutableStateFlow(true)
  private val unsynced = MutableStateFlow(UnsyncedItemCounts(0, 0))
  private var syncCalls = 0

  private fun presenter(
    counts: Flow<UnsyncedItemCounts> = unsynced,
    signOut: suspend () -> Unit = {}
  ) = AccountSyncPresenter(
    auth,
    AccountSettingsPresenter(auth, SyncActivityPresenter(syncStatus, history, held)) { syncCalls++ },
    notes,
    collections,
    bookmarks,
    counts,
    signOut
  )

  @Test fun `held rows and full activity follow live changes while recent runs stay a preview`() = runTest {
    val range = NoteRange(2, 300, 3, 1)
    val note = Note("note", listOf(range), time(100).toPlatform(), "note")
    val collection = Collection("system:test", time(100).toPlatform(), "collection")
    val bookmark = EmptyReadingBookmark(ReadingBookmarkSlot.ORANGE, "My bookmark", time(100).toPlatform(), "bookmark")
    notes.value = listOf(note)
    collections.value = listOf(CollectionWithAyahBookmarks(collection, emptyList()))
    bookmarks.value = listOf(bookmark)
    val heldNote = heldItem(SyncResourceType.NOTE, note.id)
    val heldCollection = heldItem(SyncResourceType.COLLECTION, collection.id)
    val heldBookmark = heldItem(SyncResourceType.READING_BOOKMARK, bookmark.id)
    val heldProgress = heldItem(SyncResourceType.READING_SESSION, "progress")
    val waiting = heldItem(SyncResourceType.COLLECTION_BOOKMARK, "2:255").copy(
      collectionId = collection.id,
      reason = HeldReason.WAITING_ON_PARENT,
      waitingOn = heldCollection
    )
    held.value = listOf(heldNote, waiting, heldCollection, waiting.copy(id = "2:256"), heldBookmark, heldProgress)
    history.value = listOf(
      run(400, SyncRunResult.SUCCEEDED),
      run(300, SyncRunResult.CANCELLED),
      run(200, SyncRunResult.PULLED_ONLY),
      run(100, SyncRunResult.FAILED)
    )
    val states = presenter().launchIn(backgroundScope, observing)
    runCurrent()

    var state = states.value as AccountSyncState.SignedIn
    assertEquals(4, state.blockingItemCount)
    assertEquals(listOf(heldNote, heldCollection, heldBookmark, heldProgress), state.heldRows.map { it.item })
    assertEquals(Title.Note(range), state.heldRows[0].title)
    assertEquals(Title.Collection("system:test", false), state.heldRows[1].title)
    assertEquals(Waiting(2, true), state.heldRows[1].waiting)
    assertEquals(Title.ReadingBookmark("My bookmark", ReadingBookmarkSlot.ORANGE), state.heldRows[2].title)
    assertEquals(Title.ReadingProgress, state.heldRows[3].title)
    assertEquals(history.value.take(3), state.recentRuns)
    assertEquals(history.value, state.activity.runs)
    assertEquals(held.value, state.activity.heldItems)

    notes.value = emptyList()
    collections.value = listOf(CollectionWithAyahBookmarks(collection.copy(name = "Renamed"), emptyList()))
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertEquals(Title.DeletedNote, state.heldRows[0].title)
    assertEquals(Title.Collection("Renamed", false), state.heldRows[1].title)

    history.value = listOf(run(500, SyncRunResult.SUCCEEDED)) + history.value
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertEquals(history.value, state.activity.runs)
    assertEquals(history.value.take(3), state.recentRuns)

    held.value = listOf(waiting)
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertEquals(0, state.blockingItemCount)
    assertTrue(state.heldRows.isEmpty())
  }

  @Test fun `visibility gates all data streams and resuming uses their latest values`() = runTest {
    observing.value = false
    val sources = listOf(syncStatus, history, held, notes, collections, bookmarks)
    val states = presenter().launchIn(backgroundScope, observing)
    runCurrent()
    sources.forEach { assertEquals(0, it.subscriptionCount.value) }

    observing.value = true
    runCurrent()
    sources.forEach { assertEquals(1, it.subscriptionCount.value) }
    val beforePause = (states.value as AccountSyncState.SignedIn).activity

    observing.value = false
    runCurrent()
    sources.forEach { assertEquals(0, it.subscriptionCount.value) }
    val collection = Collection("New name", time(100).toPlatform(), "collection")
    collections.value = listOf(CollectionWithAyahBookmarks(collection, emptyList()))
    held.value = listOf(heldItem(SyncResourceType.COLLECTION, collection.id))
    history.value = listOf(run(100, SyncRunResult.SUCCEEDED))
    syncStatus.value = SyncStatus.Succeeded(time(100))
    runCurrent()
    assertEquals(beforePause, (states.value as AccountSyncState.SignedIn).activity)

    observing.value = true
    runCurrent()
    val state = states.value as AccountSyncState.SignedIn
    sources.forEach { assertEquals(1, it.subscriptionCount.value) }
    assertEquals(Status.Synced(time(100)), state.activity.status)
    assertEquals(Title.Collection("New name", false), state.heldRows.single().title)
    assertEquals(history.value, state.recentRuns)
  }

  @Test fun `session end while covered stays terminal even if another sign-in follows`() = runTest {
    var signOutCalls = 0
    val counts = MutableSharedFlow<UnsyncedItemCounts>()
    val states = presenter(counts) { signOutCalls++ }.launchIn(backgroundScope, observing)
    runCurrent()
    val oldState = states.value as AccountSyncState.SignedIn
    oldState.eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    assertEquals(1, counts.subscriptionCount.value)
    observing.value = false
    runCurrent()

    auth.value = AuthState.Idle
    runCurrent()
    assertEquals(AccountSyncState.SessionEnded, states.value)
    assertEquals(0, counts.subscriptionCount.value)
    auth.value = AuthState.Success(user)
    observing.value = true
    runCurrent()
    assertEquals(AccountSyncState.SessionEnded, states.value)
    oldState.eventSink(AccountSyncEvent.SyncNow)
    oldState.eventSink(AccountSyncEvent.RequestSignOut)
    oldState.eventSink(AccountSyncEvent.ConfirmSignOut)
    runCurrent()
    assertEquals(0, syncCalls)
    assertEquals(0, signOutCalls)
    assertEquals(0, counts.subscriptionCount.value)
    listOf(syncStatus, history, held, notes, collections, bookmarks).forEach {
      assertEquals(0, it.subscriptionCount.value)
    }
  }

  @Test fun `sign-out rejects duplicate actions and allows retry after a failure`() = runTest {
    var signOutCalls = 0
    var result = CompletableDeferred<Unit>()
    val states = presenter {
      signOutCalls++
      result.await()
    }.launchIn(backgroundScope, observing)
    runCurrent()
    var state = states.value as AccountSyncState.SignedIn
    state.eventSink(AccountSyncEvent.SyncNow)
    assertEquals(1, syncCalls)
    state.eventSink(AccountSyncEvent.ConfirmSignOut)
    runCurrent()
    assertEquals(0, signOutCalls)
    state.eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    state.eventSink(AccountSyncEvent.ConfirmSignOut)
    state.eventSink(AccountSyncEvent.ConfirmSignOut)
    state.eventSink(AccountSyncEvent.SyncNow)
    runCurrent()
    assertEquals(1, signOutCalls)
    assertEquals(1, syncCalls)
    assertTrue((states.value as AccountSyncState.SignedIn).isSigningOut)

    result.completeExceptionally(IllegalStateException("Could not sign out"))
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertFalse(state.isSigningOut)
    assertEquals("Could not sign out", state.signOutError)
    state.eventSink(AccountSyncEvent.DismissSignOutError)
    runCurrent()
    assertNull((states.value as AccountSyncState.SignedIn).signOutError)

    result = CompletableDeferred()
    state.eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    state.eventSink(AccountSyncEvent.ConfirmSignOut)
    runCurrent()
    assertEquals(2, signOutCalls)
    result.complete(Unit)
    runCurrent()
    assertEquals(AccountSyncState.SessionEnded, states.value)
  }

  @Test fun `cancelling sign-out does not report a failure or end the session`() = runTest {
    val states = presenter { throw CancellationException() }.launchIn(backgroundScope, observing)
    runCurrent()
    (states.value as AccountSyncState.SignedIn).eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    (states.value as AccountSyncState.SignedIn).eventSink(AccountSyncEvent.ConfirmSignOut)
    runCurrent()
    val state = states.value as AccountSyncState.SignedIn
    assertFalse(state.isSigningOut)
    assertNull(state.signOutError)
  }

  @Test fun `duplicate requests share one read and dialog counts stay fixed until dismissed`() = runTest {
    val counts = MutableSharedFlow<UnsyncedItemCounts>()
    held.value = listOf(heldItem(SyncResourceType.COLLECTION, "collection"))
    val states = presenter(counts).launchIn(backgroundScope, observing)
    runCurrent()
    var state = states.value as AccountSyncState.SignedIn
    assertEquals(0, counts.subscriptionCount.value)
    state.eventSink(AccountSyncEvent.RequestSignOut)
    state.eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    assertNull((states.value as AccountSyncState.SignedIn).signOutWarning)
    assertEquals(1, counts.subscriptionCount.value)

    counts.emit(UnsyncedItemCounts(2, 3))
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertEquals(SignOutWarning.Unsynced(2, 1, 2), state.signOutWarning)
    assertEquals(0, counts.subscriptionCount.value)
    state.eventSink(AccountSyncEvent.RequestSignOut)
    counts.emit(UnsyncedItemCounts(0, 0))
    runCurrent()
    assertEquals(state.signOutWarning, (states.value as AccountSyncState.SignedIn).signOutWarning)
    assertEquals(0, counts.subscriptionCount.value)

    state.eventSink(AccountSyncEvent.DismissSignOutConfirmation)
    runCurrent()
    state = states.value as AccountSyncState.SignedIn
    assertNull(state.signOutWarning)
    state.eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    counts.emit(UnsyncedItemCounts(0, 0))
    runCurrent()
    assertEquals(SignOutWarning.Synced, (states.value as AccountSyncState.SignedIn).signOutWarning)
    assertEquals(0, counts.subscriptionCount.value)
  }

  @Test fun `failed reads warn but cancelled reads leave no dialog and allow another request`() = runTest {
    var reads = 0
    val counts = flow<UnsyncedItemCounts> {
      reads++
      if (reads == 1) throw CancellationException()
      error("Could not read counts")
    }
    val states = presenter(counts).launchIn(backgroundScope, observing)
    runCurrent()
    (states.value as AccountSyncState.SignedIn).eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    assertNull((states.value as AccountSyncState.SignedIn).signOutWarning)
    (states.value as AccountSyncState.SignedIn).eventSink(AccountSyncEvent.RequestSignOut)
    runCurrent()
    assertEquals(2, reads)
    assertEquals(SignOutWarning.Unknown, (states.value as AccountSyncState.SignedIn).signOutWarning)
  }

  private fun heldItem(type: SyncResourceType, id: String) = HeldSyncItem(
    type = type,
    id = id,
    collectionId = null,
    reason = HeldReason.TOO_LONG,
    waitingOn = null,
    httpStatus = null,
    serverCode = null
  )

  private fun run(endedAt: Long, result: SyncRunResult) = SyncRun(
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
}
