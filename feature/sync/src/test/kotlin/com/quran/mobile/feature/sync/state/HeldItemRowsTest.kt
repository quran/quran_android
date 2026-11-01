package com.quran.mobile.feature.sync.state

import com.quran.mobile.feature.sync.state.HeldItemRowState.Edit
import com.quran.mobile.feature.sync.state.HeldItemRowState.Title
import com.quran.mobile.feature.sync.state.HeldItemRowState.Waiting
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.EmptyReadingBookmark
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.NoteRange
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.util.toPlatform
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class HeldItemRowsTest {
  private val updatedAt = Instant.fromEpochSeconds(100).toPlatform()

  @Test fun `rows retain every blocker in source order without waiting-only items`() {
    val blockers = listOf("d", "b", "a", "c").map { held(SyncResourceType.COLLECTION, it) }
    val waiting = held(SyncResourceType.COLLECTION_BOOKMARK, "2:255", waitingOn = blockers[0])
    val items = blockers.take(1) + waiting + blockers.drop(1)

    assertEquals(blockers, heldItemRows(items, HeldItemLookup()).map { it.item })
    assertEquals(emptyList<HeldItemRowState>(), heldItemRows(listOf(waiting), HeldItemLookup()))
  }

  @Test fun `waiting matches type id and collection id rather than the entire snapshot`() {
    val first = held(SyncResourceType.COLLECTION_BOOKMARK, "2:255", collectionId = "first")
    val second = first.copy(collectionId = "second")
    val otherId = first.copy(id = "2:256")
    val parentSnapshot = first.copy(reason = HeldReason.REJECTED_BY_SERVER, httpStatus = 422)
    val waiting = held(SyncResourceType.COLLECTION_BOOKMARK, "2:257", waitingOn = parentSnapshot)
    val collection = held(SyncResourceType.COLLECTION, "shared-id")
    val note = held(SyncResourceType.NOTE, collection.id)
    val waitingOnCollection = held(SyncResourceType.READING_BOOKMARK, "bookmark", waitingOn = collection)

    val rows = heldItemRows(
      listOf(first, second, otherId, collection, note, waiting, waitingOnCollection),
      HeldItemLookup()
    )

    assertEquals(Waiting(1, areVerses = true), rows.first().waiting)
    assertNull(rows[1].waiting)
    assertNull(rows[2].waiting)
    assertEquals(Waiting(1, areVerses = false), rows[3].waiting)
    assertNull(rows[4].waiting)
  }

  @Test fun `waiting line counts verses only when every dependent is a collection bookmark`() {
    val collection = held(SyncResourceType.COLLECTION, "collection")
    val first = held(SyncResourceType.COLLECTION_BOOKMARK, "2:255", waitingOn = collection)
    val second = first.copy(id = "2:256")
    val note = held(SyncResourceType.NOTE, "note", waitingOn = collection)

    assertEquals(
      Waiting(2, areVerses = true),
      heldItemRows(listOf(collection, first, second), HeldItemLookup()).single().waiting
    )
    assertEquals(
      Waiting(3, areVerses = false),
      heldItemRows(listOf(collection, first, second, note), HeldItemLookup()).single().waiting
    )
    assertEquals(
      Waiting(1, areVerses = false),
      heldItemRows(listOf(collection, note), HeldItemLookup()).single().waiting
    )
  }

  @Test fun `waiting reasons without a parent still produce blocking rows`() {
    val items = listOf(HeldReason.WAITING_ON_PARENT, HeldReason.WAITING_ON_OTHER_ITEM).map {
      held(SyncResourceType.COLLECTION, it.name).copy(reason = it)
    }

    assertEquals(items, heldItemRows(items, HeldItemLookup()).map { it.item })
  }

  @Test fun `notes use the first range and preserve invalid verse numbers`() {
    val invalidRange = NoteRange(2, 300, 3, 1)
    val note = Note(
      body = "note",
      ranges = listOf(invalidRange, NoteRange(4, 1, 4, 2)),
      lastUpdated = updatedAt,
      id = "note"
    )
    val item = held(SyncResourceType.NOTE, note.id)

    assertEquals(Title.Note(invalidRange), HeldItemLookup(notes = listOf(note)).title(item))
    assertEquals(Title.DeletedNote, HeldItemLookup().title(item))
  }

  @Test fun `collections preserve custom names and identify Favorites for localization`() {
    val favorites = Collection("Favorites", updatedAt, "favorites", isDefault = true)
    val custom = Collection("system:test", updatedAt, "custom")
    val lookup = HeldItemLookup(collections = listOf(favorites, custom))

    assertEquals(
      Title.Collection("Favorites", isDefault = true),
      lookup.title(held(SyncResourceType.COLLECTION, favorites.id))
    )
    assertEquals(
      Title.Collection("system:test", isDefault = false),
      lookup.title(held(SyncResourceType.COLLECTION, custom.id))
    )
    assertEquals(Title.DeletedCollection, lookup.title(held(SyncResourceType.COLLECTION, "missing")))
  }

  @Test fun `reading bookmarks retain names and fall back to their slot when unnamed`() {
    for ((name, expectedName) in listOf("My bookmark" to "My bookmark", "" to null, null to null)) {
      val bookmark = EmptyReadingBookmark(ReadingBookmarkSlot.ORANGE, name, updatedAt, "bookmark")
      val lookup = HeldItemLookup(readingBookmarks = listOf(bookmark))
      val item = held(SyncResourceType.READING_BOOKMARK, bookmark.id)

      assertEquals(
        Title.ReadingBookmark(expectedName, ReadingBookmarkSlot.ORANGE),
        lookup.title(item)
      )
      assertEquals(Title.DeletedBookmark, HeldItemLookup().title(item))
    }
  }

  @Test fun `collection bookmarks preserve their raw id without requiring a local item`() {
    val lookup = HeldItemLookup()
    for (verse in listOf("2:255", "2:300", "invalid")) {
      assertEquals(Title.Bookmark(verse), lookup.title(held(SyncResourceType.COLLECTION_BOOKMARK, verse)))
    }
    assertEquals(Title.ReadingProgress, lookup.title(held(SyncResourceType.READING_SESSION, "session")))
  }

  @Test fun `edit is offered only for reasons that renaming can fix`() {
    val collection = Collection("system:test", updatedAt, "collection")
    val bookmark = EmptyReadingBookmark(ReadingBookmarkSlot.TEAL, null, updatedAt, "bookmark")
    val lookup = HeldItemLookup(collections = listOf(collection), readingBookmarks = listOf(bookmark))
    val collectionReasons = setOf(
      HeldReason.TOO_SHORT, HeldReason.TOO_LONG, HeldReason.RESERVED_NAME,
      HeldReason.REJECTED_BY_SERVER, HeldReason.WAITING_ON_OTHER_ITEM
    )
    val bookmarkReasons = setOf(HeldReason.TOO_LONG, HeldReason.REJECTED_BY_SERVER)
    for (reason in HeldReason.entries) {
      val collectionRow = heldItemRows(
        listOf(held(SyncResourceType.COLLECTION, collection.id).copy(reason = reason)), lookup
      ).single()
      val bookmarkRow = heldItemRows(
        listOf(held(SyncResourceType.READING_BOOKMARK, bookmark.id).copy(reason = reason)), lookup
      ).single()
      assertEquals(
        reason.name,
        if (reason in collectionReasons) Edit.Collection(collection.id, collection.name) else null,
        collectionRow.edit
      )
      assertEquals(
        reason.name,
        if (reason in bookmarkReasons) Edit.ReadingBookmark(bookmark.id, bookmark.slot, "") else null,
        bookmarkRow.edit
      )
    }
  }

  @Test fun `protected collections and missing items cannot be edited`() {
    val favorites = Collection("Favorites", updatedAt, "favorites", isDefault = true)
    val readOnly = Collection("Shared", updatedAt, "shared", isReadOnly = true)
    val lookup = HeldItemLookup(collections = listOf(favorites, readOnly))
    for (id in listOf(favorites.id, readOnly.id, "deleted")) {
      assertNull(lookup.edit(held(SyncResourceType.COLLECTION, id).copy(reason = HeldReason.TOO_LONG)))
    }
    assertNull(lookup.edit(held(SyncResourceType.READING_BOOKMARK, "deleted").copy(reason = HeldReason.TOO_LONG)))
  }

  @Test fun `notes verses and progress have no editor`() {
    val note = Note("short", listOf(NoteRange(2, 255, 2, 255)), updatedAt, "note")
    val lookup = HeldItemLookup(notes = listOf(note))
    for (type in listOf(SyncResourceType.NOTE, SyncResourceType.COLLECTION_BOOKMARK, SyncResourceType.READING_SESSION)) {
      for (reason in HeldReason.entries) {
        assertNull(lookup.edit(held(type, note.id).copy(reason = reason)))
      }
    }
  }

  private fun held(
    type: SyncResourceType,
    id: String,
    collectionId: String? = null,
    waitingOn: HeldSyncItem? = null
  ) = HeldSyncItem(
    type = type,
    id = id,
    collectionId = collectionId,
    reason = if (waitingOn == null) HeldReason.INVALID_VERSE else HeldReason.WAITING_ON_PARENT,
    waitingOn = waitingOn,
    httpStatus = null,
    serverCode = null
  )
}
