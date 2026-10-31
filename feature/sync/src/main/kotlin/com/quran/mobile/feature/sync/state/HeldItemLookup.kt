package com.quran.mobile.feature.sync.state

import com.quran.mobile.feature.sync.state.HeldItemRowState.Title
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.ReadingBookmark
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
