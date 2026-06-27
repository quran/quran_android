@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.di.AppCoroutineScope
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.util.toPlatform
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Shared live state for collection bookmark membership.
 *
 * Implementations build this state from persistence repositories or delegate to mobile-sync's
 * combined flow. The state includes the virtual default collection so subscribers and write
 * decisions do not need a second membership query.
 */
interface BookmarkCollectionsState {
  /**
   * Shared app-scoped collection membership stream.
   *
   * A null value means the upstream collection flow has not emitted yet.
   */
  val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?>

  /**
   * Returns collection membership for one-shot DAO calls.
   *
   * Implementations should use the same ownership boundary as [collectionsWithBookmarks]. The
   * sync-backed implementation awaits the shared app state, while the repository-backed fallback
   * reads persistence directly so immediate read-after-write calls keep their existing behavior.
   */
  suspend fun currentCollectionsWithBookmarks(): List<CollectionWithAyahBookmarks>
}

/**
 * Builds shared collection bookmark state from the local mobile-sync persistence repositories.
 *
 * This is the fallback implementation used when the app is not backed by the sync service. It
 * mirrors the service state closely enough for app code: default collection first, then custom
 * collections with their membership edges. The [collectionsWithBookmarks] flow is hot and shared in
 * app scope; [currentCollectionsWithBookmarks] reads persistence directly to preserve existing
 * immediate read-after-write behavior in local mode.
 *
 * @param collectionsRepository source for persisted custom collections.
 * @param collectionBookmarksRepository source for bookmark membership edges.
 */
class RepositoryBookmarkCollectionsState(
  private val collectionsRepository: CollectionsRepository,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  appCoroutineScope: AppCoroutineScope
) : BookmarkCollectionsState {
  override val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?> =
    collectionsWithBookmarksFlow()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override suspend fun currentCollectionsWithBookmarks(): List<CollectionWithAyahBookmarks> {
    val customCollections = collectionsRepository.getAllCollections()
      .filterNot { collection -> collection.isDefault }
    val defaultBookmarks = collectionBookmarksRepository.getBookmarksForCollection(DEFAULT_BOOKMARK_COLLECTION_ID)
    return listOf(defaultCollection(defaultBookmarks)) +
      customCollections.map { collection ->
        CollectionWithAyahBookmarks(
          collection = collection,
          bookmarks = collectionBookmarksRepository.getBookmarksForCollection(collection.localId)
        )
      }
  }

  private fun collectionsWithBookmarksFlow(): Flow<List<CollectionWithAyahBookmarks>> {
    return collectionsRepository.getCollectionsFlow()
      .flatMapLatest { collections ->
        val customCollections = collections.filterNot { collection -> collection.isDefault }
        val defaultCollectionFlow = collectionBookmarksRepository
          .getBookmarksForCollectionFlow(DEFAULT_BOOKMARK_COLLECTION_ID)
          .map(::defaultCollection)
        val customCollectionFlows = customCollections.map { collection ->
          collectionBookmarksRepository.getBookmarksForCollectionFlow(collection.localId)
            .map { bookmarks ->
              CollectionWithAyahBookmarks(collection, bookmarks)
            }
        }
        if (customCollectionFlows.isEmpty()) {
          defaultCollectionFlow.map { defaultCollection -> listOf(defaultCollection) }
        } else {
          combine(listOf(defaultCollectionFlow) + customCollectionFlows) { collectionsWithBookmarks ->
            collectionsWithBookmarks.toList()
          }
        }
      }
      .distinctUntilChanged()
  }

  private fun defaultCollection(bookmarks: List<CollectionAyahBookmark>): CollectionWithAyahBookmarks {
    return CollectionWithAyahBookmarks(
      collection = Collection(
        name = DEFAULT_COLLECTION_NAME,
        lastUpdated = bookmarks.firstOrNull()?.lastUpdated ?: EMPTY_DEFAULT_COLLECTION_TIMESTAMP,
        localId = DEFAULT_BOOKMARK_COLLECTION_ID
      ),
      bookmarks = bookmarks
    )
  }

  private companion object {
    private const val DEFAULT_COLLECTION_NAME = "Default"
    private val EMPTY_DEFAULT_COLLECTION_TIMESTAMP = Instant.fromEpochMilliseconds(0).toPlatform()
  }
}
