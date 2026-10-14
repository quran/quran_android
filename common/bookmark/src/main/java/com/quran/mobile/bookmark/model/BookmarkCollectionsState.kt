package com.quran.mobile.bookmark.model

import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

interface BookmarkCollectionsState {
  val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?>

  suspend fun currentCollectionsWithBookmarks(): List<CollectionWithAyahBookmarks> {
    return collectionsWithBookmarks.value
      ?: collectionsWithBookmarks.filterNotNull().first()
  }
}
