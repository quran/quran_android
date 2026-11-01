package com.quran.mobile.feature.sync.state

import com.quran.shared.persistence.model.NoteRange
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncResourceType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal data class HeldItemRowState(
  val item: HeldSyncItem,
  val title: Title,
  val waiting: Waiting?,
  val edit: Edit? = null
) {
  sealed interface Edit {
    data class Note(val id: String) : Edit
    data class Collection(val id: String, val name: String) : Edit
    data class ReadingBookmark(val id: String, val slot: ReadingBookmarkSlot, val name: String) : Edit
  }

  sealed interface Title {
    data class Note(val range: NoteRange) : Title
    data class Collection(val name: String, val isDefault: Boolean) : Title
    data class Bookmark(val verse: String) : Title
    data class ReadingBookmark(val name: String?, val slot: ReadingBookmarkSlot) : Title
    data object ReadingProgress : Title
    data object DeletedNote : Title
    data object DeletedCollection : Title
    data object DeletedBookmark : Title
  }

  data class Waiting(val count: Int, val areVerses: Boolean)
}

internal fun heldItemRows(
  items: List<HeldSyncItem>,
  lookup: HeldItemLookup
): ImmutableList<HeldItemRowState> {
  return items.filter { it.waitingOn == null }.map { item ->
    val waitingItems = items.filter {
      it.waitingOn?.let { parent ->
        parent.type == item.type && parent.id == item.id && parent.collectionId == item.collectionId
      } == true
    }
    HeldItemRowState(
      item = item,
      title = lookup.title(item),
      edit = lookup.edit(item),
      waiting = if (waitingItems.isEmpty()) null else HeldItemRowState.Waiting(
        count = waitingItems.size,
        areVerses = waitingItems.all { it.type == SyncResourceType.COLLECTION_BOOKMARK }
      )
    )
  }.toImmutableList()
}
