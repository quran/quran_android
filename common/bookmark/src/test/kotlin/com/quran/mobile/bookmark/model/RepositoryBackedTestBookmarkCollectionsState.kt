@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quran.mobile.bookmark.model

import com.quran.data.di.AppCoroutineScope
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Repository-backed test adapter that mirrors the collection section exposed by `QuranDataService`.
 *
 * Lower-boundary DAO tests use raw persistence repositories intentionally; production code always
 * obtains [BookmarkCollectionsState] from the managed mobile-sync service.
 */
internal class RepositoryBackedTestBookmarkCollectionsState(
  private val collectionsRepository: CollectionsRepository,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  appCoroutineScope: AppCoroutineScope
) : BookmarkCollectionsState {
  override val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?> =
    collectionsWithBookmarksFlow()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override suspend fun currentCollectionsWithBookmarks(): List<CollectionWithAyahBookmarks> {
    return bookmarkCollections(collectionsRepository.getAllCollections()).map { collection ->
      CollectionWithAyahBookmarks(
        collection = collection,
        bookmarks = collectionBookmarksRepository.getBookmarksForCollection(collection.id)
      )
    }
  }

  private fun collectionsWithBookmarksFlow(): Flow<List<CollectionWithAyahBookmarks>> {
    return collectionsRepository.getCollectionsFlow()
      .flatMapLatest { collections ->
        val collectionFlows = bookmarkCollections(collections).map { collection ->
          collectionBookmarksRepository.getBookmarksForCollectionFlow(collection.id)
            .map { bookmarks -> CollectionWithAyahBookmarks(collection, bookmarks) }
        }
        if (collectionFlows.isEmpty()) {
          flowOf(emptyList())
        } else {
          combine(collectionFlows) { collectionsWithBookmarks ->
            collectionsWithBookmarks.toList()
          }
        }
      }
      .distinctUntilChanged()
  }

  private fun bookmarkCollections(collections: List<Collection>): List<Collection> {
    return collections
      .filter { collection -> collection.isDefault || !collection.isSystem }
      .sortedByDescending { collection -> collection.isDefault }
  }
}
