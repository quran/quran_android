package com.quran.mobile.feature.sync.state

import com.quran.mobile.feature.sync.state.HeldItemRowState.Edit
import com.quran.mobile.feature.sync.state.HeldItemRowState.Title
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.ReadingBookmark
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncResourceType

internal class HeldItemLookup(
  notes: List<Note> = emptyList(),
  collections: List<Collection> = emptyList(),
  readingBookmarks: List<ReadingBookmark> = emptyList()
) {
  private val notesById = notes.associateBy { it.id }
  private val collectionsById = collections.associateBy { it.id }
  private val readingBookmarksById = readingBookmarks.associateBy { it.id }

  fun edit(item: HeldSyncItem): Edit? = when (item.type) {
    SyncResourceType.COLLECTION -> when (item.reason) {
      HeldReason.TOO_SHORT, HeldReason.TOO_LONG, HeldReason.RESERVED_NAME,
      HeldReason.REJECTED_BY_SERVER, HeldReason.WAITING_ON_OTHER_ITEM -> collectionsById[item.id]
        ?.takeUnless { it.isDefault || it.isReadOnly }
        ?.let { Edit.Collection(it.id, it.name) }
      else -> null
    }
    SyncResourceType.READING_BOOKMARK -> when (item.reason) {
      HeldReason.TOO_LONG, HeldReason.REJECTED_BY_SERVER -> readingBookmarksById[item.id]
        ?.let { Edit.ReadingBookmark(it.id, it.slot, it.name.orEmpty()) }
      else -> null
    }
    // Notes can be edited here once Android has a note editor.
    SyncResourceType.NOTE, SyncResourceType.COLLECTION_BOOKMARK, SyncResourceType.READING_SESSION -> null
  }

  fun title(item: HeldSyncItem): Title = when (item.type) {
    SyncResourceType.NOTE -> notesById[item.id]?.ranges?.firstOrNull()?.let {
      Title.Note(it)
    } ?: Title.DeletedNote
    SyncResourceType.COLLECTION -> collectionsById[item.id]?.let {
      Title.Collection(it.name, it.isDefault)
    } ?: Title.DeletedCollection
    SyncResourceType.COLLECTION_BOOKMARK -> Title.Bookmark(item.id)
    SyncResourceType.READING_BOOKMARK -> readingBookmarksById[item.id]?.let {
      Title.ReadingBookmark(it.name?.ifEmpty { null }, it.slot)
    } ?: Title.DeletedBookmark
    SyncResourceType.READING_SESSION -> Title.ReadingProgress
  }
}
