package com.quran.mobile.feature.sync

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.DefaultMobileSyncRepositoryProvider
import com.quran.mobile.bookmark.di.MobileSyncRepositoryProvider
import com.quran.mobile.bookmark.model.BookmarkCollectionsState
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.BookmarkCollectionsReplacementResult
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.model.ReadingBookmark
import com.quran.shared.persistence.model.ReadingSession
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.pipeline.QuranDataService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@ContributesBinding(
  AppScope::class,
  replaces = [DefaultMobileSyncRepositoryProvider::class]
)
class QuranSyncRepositoryProvider @Inject constructor(
  syncManager: QuranSyncManager,
  appCoroutineScope: AppCoroutineScope
) : MobileSyncRepositoryProvider {
  private val quranDataService = syncManager.quranDataService

  override val bookmarksRepository: BookmarksRepository =
    SyncBookmarksRepository(quranDataService)

  override val collectionsRepository: CollectionsRepository =
    SyncCollectionsRepository(quranDataService)

  override val collectionBookmarksRepository: CollectionBookmarksRepository =
    SyncCollectionBookmarksRepository(quranDataService)

  override val bookmarkCollectionsState: BookmarkCollectionsState =
    SyncBookmarkCollectionsState(quranDataService, appCoroutineScope)

  override val readingBookmarksRepository: ReadingBookmarksRepository =
    SyncReadingBookmarksRepository(quranDataService)

  override val readingSessionsRepository: ReadingSessionsRepository =
    SyncReadingSessionsRepository(quranDataService)
}

private class SyncBookmarksRepository(
  private val quranDataService: QuranDataService
) : BookmarksRepository {
  override suspend fun getAllBookmarks(): List<AyahBookmark> {
    return quranDataService.bookmarks.first()
  }

  override fun getBookmarksFlow(): Flow<List<AyahBookmark>> {
    return quranDataService.bookmarks
  }

  override suspend fun addBookmark(sura: Int, ayah: Int): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah)
  }

  override suspend fun addBookmark(sura: Int, ayah: Int, timestamp: PlatformDateTime): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah, timestamp)
  }

  override suspend fun addBookmark(sura: Int, ayah: Int, collectionIds: List<String>): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah, collectionIds)
  }

  override suspend fun addBookmark(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>,
    timestamp: PlatformDateTime
  ): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah, collectionIds, timestamp)
  }

  override suspend fun replaceBookmarkCollections(
    id: String,
    collectionIds: List<String>
  ): Boolean {
    return quranDataService.replaceBookmarkCollections(id, collectionIds)
  }

  override suspend fun replaceBookmarkCollections(
    id: String,
    collectionIds: List<String>,
    timestamp: PlatformDateTime
  ): Boolean {
    return quranDataService.replaceBookmarkCollections(id, collectionIds, timestamp)
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>
  ): BookmarkCollectionsReplacementResult {
    val changed = ayahBookmarkMemberships(sura, ayah) != normalizedCollectionIds(collectionIds)
    return BookmarkCollectionsReplacementResult(
      bookmark = quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionIds),
      changed = changed
    )
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>,
    timestamp: PlatformDateTime
  ): BookmarkCollectionsReplacementResult {
    val changed = ayahBookmarkMemberships(sura, ayah) != normalizedCollectionIds(collectionIds)
    return BookmarkCollectionsReplacementResult(
      bookmark = quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionIds, timestamp),
      changed = changed
    )
  }

  override suspend fun deleteBookmark(sura: Int, ayah: Int): Boolean {
    return quranDataService.deleteBookmark(sura, ayah)
  }

  override suspend fun deleteBookmark(bookmark: AyahBookmark): Boolean {
    return quranDataService.deleteBookmark(bookmark)
  }

  override suspend fun deleteBookmark(id: String): Boolean {
    return quranDataService.deleteBookmark(id)
  }

  private suspend fun ayahBookmarkMemberships(sura: Int, ayah: Int): Set<String>? {
    val bookmark = getAllBookmarks()
      .firstOrNull { bookmark -> bookmark.sura == sura && bookmark.ayah == ayah }
      ?: return null
    val bookmarkId = bookmark.id
    return quranDataService.collectionsWithBookmarks.first()
      .mapNotNull { collectionWithBookmarks ->
        collectionWithBookmarks.collection.id
          .takeIf {
            collectionWithBookmarks.bookmarks.any { collectionBookmark ->
              collectionBookmark.bookmarkId == bookmarkId
            }
          }
      }
      .toSet()
  }

  private fun normalizedCollectionIds(collectionIds: List<String>): Set<String> {
    return collectionIds
      .map { collectionId -> collectionId.trim() }
      .filter { collectionId -> collectionId.isNotEmpty() }
      .distinct()
      .ifEmpty { listOf(quranDataService.defaultCollectionId) }
      .toSet()
  }
}

/**
 * Delegates shared collection membership state to mobile-sync's combined flow.
 *
 * That flow includes the virtual default collection, so callers do not need a second default
 * membership query. The state is app-scoped so multiple app subscribers share one upstream
 * mobile-sync collection.
 */
private class SyncBookmarkCollectionsState(
  private val quranDataService: QuranDataService,
  appCoroutineScope: AppCoroutineScope
) : BookmarkCollectionsState {
  override val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?> =
    quranDataService.collectionsWithBookmarks
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override suspend fun currentCollectionsWithBookmarks(): List<CollectionWithAyahBookmarks> {
    return collectionsWithBookmarks
      .filterNotNull()
      .first()
  }
}

private class SyncCollectionsRepository(
  private val quranDataService: QuranDataService
) : CollectionsRepository {
  override suspend fun getAllCollections(): List<Collection> {
    return quranDataService.collectionsWithBookmarks.first().map { it.collection }
  }

  override suspend fun addCollection(name: String): Collection {
    return quranDataService.addCollection(name)
  }

  override suspend fun addCollection(name: String, timestamp: PlatformDateTime): Collection {
    return quranDataService.addCollection(name, timestamp)
  }

  override suspend fun updateCollection(id: String, name: String): Collection {
    return quranDataService.updateCollection(id, name)
  }

  override suspend fun updateCollection(
    id: String,
    name: String,
    timestamp: PlatformDateTime
  ): Collection {
    return quranDataService.updateCollection(id, name, timestamp)
  }

  override suspend fun deleteCollection(id: String): Boolean {
    return quranDataService.deleteCollection(id)
  }

  override fun getCollectionsFlow(): Flow<List<Collection>> {
    return quranDataService.collectionsWithBookmarks.map { collections ->
      collections.map { it.collection }
    }
  }
}

private class SyncCollectionBookmarksRepository(
  private val quranDataService: QuranDataService
) : CollectionBookmarksRepository {
  override suspend fun getBookmarksForCollection(collectionId: String): List<CollectionAyahBookmark> {
    return quranDataService.getBookmarksForCollectionFlow(collectionId).first()
  }

  override suspend fun addBookmarkToCollection(
    collectionId: String,
    bookmark: AyahBookmark
  ): CollectionAyahBookmark {
    quranDataService.addBookmarkToCollection(collectionId, bookmark)
    return collectionBookmarkFor(collectionId, bookmark)
  }

  override suspend fun addBookmarkToCollection(
    collectionId: String,
    bookmark: AyahBookmark,
    timestamp: PlatformDateTime
  ): CollectionAyahBookmark {
    quranDataService.addBookmarkToCollection(collectionId, bookmark, timestamp)
    return collectionBookmarkFor(collectionId, bookmark)
  }

  override suspend fun addAyahBookmarkToCollection(
    collectionId: String,
    sura: Int,
    ayah: Int
  ): CollectionAyahBookmark {
    return quranDataService.addAyahBookmarkToCollection(collectionId, sura, ayah)
  }

  override suspend fun addAyahBookmarkToCollection(
    collectionId: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): CollectionAyahBookmark {
    return quranDataService.addAyahBookmarkToCollection(collectionId, sura, ayah, timestamp)
  }

  override suspend fun removeBookmarkFromCollection(
    collectionId: String,
    bookmark: AyahBookmark
  ): Boolean {
    quranDataService.removeBookmarkFromCollection(collectionId, bookmark)
    return true
  }

  override suspend fun removeAyahBookmarkFromCollection(
    collectionAyahBookmark: CollectionAyahBookmark
  ): Boolean {
    quranDataService.removeAyahBookmarkFromCollection(collectionAyahBookmark)
    return true
  }

  override fun getBookmarksForCollectionFlow(collectionId: String): Flow<List<CollectionAyahBookmark>> {
    return quranDataService.getBookmarksForCollectionFlow(collectionId)
  }

  private suspend fun collectionBookmarkFor(
    collectionId: String,
    bookmark: AyahBookmark
  ): CollectionAyahBookmark {
    return getBookmarksForCollection(collectionId)
      .firstOrNull { collectionBookmark -> collectionBookmark.bookmarkId == bookmark.id }
      ?: error("Expected bookmark ${bookmark.id} in collection $collectionId after sync-service write.")
  }
}

private class SyncReadingBookmarksRepository(
  private val quranDataService: QuranDataService
) : ReadingBookmarksRepository {
  override suspend fun getReadingBookmark(): ReadingBookmark? {
    return quranDataService.readingBookmark.first()
  }

  override fun getReadingBookmarkFlow(): Flow<ReadingBookmark?> {
    return quranDataService.readingBookmark
  }

  override suspend fun addAyahReadingBookmark(sura: Int, ayah: Int) =
    quranDataService.addAyahReadingBookmark(sura, ayah)

  override suspend fun addAyahReadingBookmark(sura: Int, ayah: Int, timestamp: PlatformDateTime) =
    quranDataService.addAyahReadingBookmark(sura, ayah, timestamp)

  override suspend fun addPageReadingBookmark(page: Int) =
    quranDataService.addPageReadingBookmark(page)

  override suspend fun addPageReadingBookmark(page: Int, timestamp: PlatformDateTime) =
    quranDataService.addPageReadingBookmark(page, timestamp)

  override suspend fun deleteReadingBookmark(): Boolean {
    return quranDataService.deleteReadingBookmark()
  }
}

private class SyncReadingSessionsRepository(
  private val quranDataService: QuranDataService
) : ReadingSessionsRepository {
  override suspend fun getReadingSessions(): List<ReadingSession> {
    return quranDataService.readingSessions.first()
  }

  override suspend fun addReadingSession(sura: Int, ayah: Int): ReadingSession {
    return quranDataService.addReadingSession(sura, ayah)
  }

  override suspend fun addReadingSession(
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): ReadingSession {
    return quranDataService.addReadingSession(sura, ayah, timestamp)
  }

  override suspend fun updateReadingSession(
    id: String,
    sura: Int,
    ayah: Int
  ): ReadingSession {
    return quranDataService.updateReadingSession(id, sura, ayah)
  }

  override suspend fun updateReadingSession(
    id: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): ReadingSession {
    return quranDataService.updateReadingSession(id, sura, ayah, timestamp)
  }

  override fun getReadingSessionsFlow(): Flow<List<ReadingSession>> {
    return quranDataService.readingSessions
  }

  override suspend fun deleteReadingSession(sura: Int, ayah: Int): Boolean {
    return quranDataService.deleteReadingSession(sura, ayah)
  }
}
