package com.quran.mobile.feature.sync

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.MobileSyncRepositoryProvider
import com.quran.mobile.bookmark.model.BookmarkCollectionsState
import com.quran.shared.persistence.model.AyahHighlight
import com.quran.shared.persistence.model.AyahHighlightColor
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
@ContributesBinding(AppScope::class)
@Inject
class QuranSyncRepositoryProvider(
  syncManager: QuranSyncManager,
  appCoroutineScope: AppCoroutineScope
) : MobileSyncRepositoryProvider {
  private val quranDataService = syncManager.quranDataService

  override val bookmarksRepository: BookmarksRepository =
    SyncBookmarksRepository(quranDataService)

  override val bookmarkCollectionsState: BookmarkCollectionsState =
    SyncBookmarkCollectionsState(quranDataService, appCoroutineScope)

  override val collectionsRepository: CollectionsRepository =
    SyncCollectionsRepository(quranDataService, bookmarkCollectionsState)

  override val collectionBookmarksRepository: CollectionBookmarksRepository =
    SyncCollectionBookmarksRepository(quranDataService, bookmarkCollectionsState)

  override val readingBookmarksRepository: ReadingBookmarksRepository =
    SyncReadingBookmarksRepository(quranDataService)

  override val readingSessionsRepository: ReadingSessionsRepository =
    SyncReadingSessionsRepository(quranDataService)
}

private class SyncBookmarksRepository(
  private val quranDataService: QuranDataService
) : BookmarksRepository {
  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>
  ): BookmarkCollectionsReplacementResult {
    return quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionIds)
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>,
    timestamp: PlatformDateTime
  ): BookmarkCollectionsReplacementResult {
    return quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionIds, timestamp)
  }
}

private class SyncBookmarkCollectionsState(
  quranDataService: QuranDataService,
  appCoroutineScope: AppCoroutineScope
) : BookmarkCollectionsState {
  override val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?> =
    quranDataService.collectionsWithBookmarks
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)
}

private class SyncCollectionsRepository(
  private val quranDataService: QuranDataService,
  private val bookmarkCollectionsState: BookmarkCollectionsState
) : CollectionsRepository {
  override suspend fun getAllCollections(): List<Collection> {
    return bookmarkCollectionsState.currentCollectionsWithBookmarks().map { it.collection }
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
    return bookmarkCollectionsState.collectionsWithBookmarks.filterNotNull().map { collections ->
      collections.map { it.collection }
    }
  }
}

private class SyncCollectionBookmarksRepository(
  private val quranDataService: QuranDataService,
  private val bookmarkCollectionsState: BookmarkCollectionsState
) : CollectionBookmarksRepository {
  override suspend fun getBookmarksForCollection(collectionId: String): List<CollectionAyahBookmark> {
    return bookmarkCollectionsState.currentCollectionsWithBookmarks()
      .firstOrNull { collection -> collection.collection.id == collectionId }
      ?.bookmarks
      .orEmpty()
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

  override suspend fun removeAyahBookmarkFromCollection(
    collectionAyahBookmark: CollectionAyahBookmark
  ): Boolean {
    quranDataService.removeAyahBookmarkFromCollection(collectionAyahBookmark)
    return true
  }

  override fun getBookmarksForCollectionFlow(collectionId: String): Flow<List<CollectionAyahBookmark>> {
    return bookmarkCollectionsState.collectionsWithBookmarks
      .filterNotNull()
      .map { collections ->
        collections
          .firstOrNull { collection -> collection.collection.id == collectionId }
          ?.bookmarks
          .orEmpty()
      }
      .distinctUntilChanged()
  }

  override fun getHighlightsFlow(): Flow<List<AyahHighlight>> {
    return quranDataService.highlights
  }

  override suspend fun setHighlight(
    sura: Int,
    ayah: Int,
    color: AyahHighlightColor,
    timestamp: PlatformDateTime
  ): AyahHighlight {
    return quranDataService.setHighlight(sura, ayah, color)
  }

  override suspend fun removeHighlight(
    sura: Int,
    ayah: Int
  ): Boolean {
    return quranDataService.removeHighlight(sura, ayah)
  }

  override suspend fun removeHighlight(
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): Boolean {
    return quranDataService.removeHighlight(sura, ayah)
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
