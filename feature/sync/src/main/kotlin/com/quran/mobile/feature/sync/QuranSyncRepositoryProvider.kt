package com.quran.mobile.feature.sync

import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.DefaultMobileSyncRepositoryProvider
import com.quran.mobile.bookmark.di.MobileSyncRepositoryProvider
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.BookmarkCollectionsReplacementResult
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionAyahBookmark
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@ContributesBinding(
  AppScope::class,
  replaces = [DefaultMobileSyncRepositoryProvider::class]
)
class QuranSyncRepositoryProvider @Inject constructor(
  syncManager: QuranSyncManager
) : MobileSyncRepositoryProvider {
  private val quranDataService = syncManager.quranDataService

  override val bookmarksRepository: BookmarksRepository =
    SyncBookmarksRepository(quranDataService)

  override val collectionsRepository: CollectionsRepository =
    SyncCollectionsRepository(quranDataService)

  override val collectionBookmarksRepository: CollectionBookmarksRepository =
    SyncCollectionBookmarksRepository(quranDataService)

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

  override suspend fun addBookmark(sura: Int, ayah: Int, collectionLocalIds: List<String>?): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah, collectionLocalIds)
  }

  override suspend fun addBookmark(
    sura: Int,
    ayah: Int,
    collectionLocalIds: List<String>?,
    timestamp: PlatformDateTime
  ): AyahBookmark {
    return quranDataService.addBookmark(sura, ayah, collectionLocalIds, timestamp)
  }

  override suspend fun replaceBookmarkCollections(
    localId: String,
    collectionLocalIds: List<String>?
  ): Boolean {
    return quranDataService.replaceBookmarkCollections(localId, collectionLocalIds)
  }

  override suspend fun replaceBookmarkCollections(
    localId: String,
    collectionLocalIds: List<String>?,
    timestamp: PlatformDateTime
  ): Boolean {
    return quranDataService.replaceBookmarkCollections(localId, collectionLocalIds, timestamp)
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionLocalIds: List<String>?
  ): BookmarkCollectionsReplacementResult {
    val changed = ayahBookmarkMemberships(sura, ayah) != normalizedCollectionIds(collectionLocalIds)
    return BookmarkCollectionsReplacementResult(
      bookmark = quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionLocalIds),
      changed = changed
    )
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionLocalIds: List<String>?,
    timestamp: PlatformDateTime
  ): BookmarkCollectionsReplacementResult {
    val changed = ayahBookmarkMemberships(sura, ayah) != normalizedCollectionIds(collectionLocalIds)
    return BookmarkCollectionsReplacementResult(
      bookmark = quranDataService.replaceAyahBookmarkCollections(sura, ayah, collectionLocalIds, timestamp),
      changed = changed
    )
  }

  override suspend fun deleteBookmark(sura: Int, ayah: Int): Boolean {
    return quranDataService.deleteBookmark(sura, ayah)
  }

  override suspend fun deleteBookmark(bookmark: AyahBookmark): Boolean {
    return quranDataService.deleteBookmark(bookmark)
  }

  override suspend fun deleteBookmark(localId: String): Boolean {
    return quranDataService.deleteBookmark(localId)
  }

  private suspend fun ayahBookmarkMemberships(sura: Int, ayah: Int): Set<String>? {
    val bookmark = getAllBookmarks()
      .firstOrNull { bookmark -> bookmark.sura == sura && bookmark.ayah == ayah }
      ?: return null
    val bookmarkLocalId = bookmark.localId
    return buildSet {
      if (defaultBookmarkIds().contains(bookmarkLocalId)) {
        add(quranDataService.defaultCollectionId)
      }
      quranDataService.collectionsWithBookmarks.first().forEach { collectionWithBookmarks ->
        if (collectionWithBookmarks.bookmarks.any { it.bookmarkLocalId == bookmarkLocalId }) {
          add(collectionWithBookmarks.collection.localId)
        }
      }
    }
  }

  private suspend fun defaultBookmarkIds(): Set<String> {
    return quranDataService.getBookmarksForCollectionFlow(quranDataService.defaultCollectionId)
      .first()
      .map { bookmark -> bookmark.bookmarkLocalId }
      .toSet()
  }

  private fun normalizedCollectionIds(collectionLocalIds: List<String>?): Set<String> {
    return collectionLocalIds
      ?.map { collectionLocalId -> collectionLocalId.trim() }
      ?.filter { collectionLocalId -> collectionLocalId.isNotEmpty() }
      ?.distinct()
      .orEmpty()
      .ifEmpty { listOf(quranDataService.defaultCollectionId) }
      .toSet()
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

  override suspend fun updateCollection(localId: String, name: String): Collection {
    return quranDataService.updateCollection(localId, name)
  }

  override suspend fun updateCollection(
    localId: String,
    name: String,
    timestamp: PlatformDateTime
  ): Collection {
    return quranDataService.updateCollection(localId, name, timestamp)
  }

  override suspend fun deleteCollection(localId: String): Boolean {
    return quranDataService.deleteCollection(localId)
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
  override suspend fun getBookmarksForCollection(collectionLocalId: String): List<CollectionAyahBookmark> {
    return quranDataService.getBookmarksForCollectionFlow(collectionLocalId).first()
  }

  override suspend fun addBookmarkToCollection(
    collectionLocalId: String,
    bookmark: AyahBookmark
  ): CollectionAyahBookmark {
    quranDataService.addBookmarkToCollection(collectionLocalId, bookmark)
    return collectionBookmarkFor(collectionLocalId, bookmark)
  }

  override suspend fun addBookmarkToCollection(
    collectionLocalId: String,
    bookmark: AyahBookmark,
    timestamp: PlatformDateTime
  ): CollectionAyahBookmark {
    quranDataService.addBookmarkToCollection(collectionLocalId, bookmark, timestamp)
    return collectionBookmarkFor(collectionLocalId, bookmark)
  }

  override suspend fun addAyahBookmarkToCollection(
    collectionLocalId: String,
    sura: Int,
    ayah: Int
  ): CollectionAyahBookmark {
    return quranDataService.addAyahBookmarkToCollection(collectionLocalId, sura, ayah)
  }

  override suspend fun addAyahBookmarkToCollection(
    collectionLocalId: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): CollectionAyahBookmark {
    return quranDataService.addAyahBookmarkToCollection(collectionLocalId, sura, ayah, timestamp)
  }

  override suspend fun removeBookmarkFromCollection(
    collectionLocalId: String,
    bookmark: AyahBookmark
  ): Boolean {
    quranDataService.removeBookmarkFromCollection(collectionLocalId, bookmark)
    return true
  }

  override suspend fun removeAyahBookmarkFromCollection(
    collectionAyahBookmark: CollectionAyahBookmark
  ): Boolean {
    quranDataService.removeAyahBookmarkFromCollection(collectionAyahBookmark)
    return true
  }

  override fun getBookmarksForCollectionFlow(collectionLocalId: String): Flow<List<CollectionAyahBookmark>> {
    return quranDataService.getBookmarksForCollectionFlow(collectionLocalId)
  }

  private suspend fun collectionBookmarkFor(
    collectionLocalId: String,
    bookmark: AyahBookmark
  ): CollectionAyahBookmark {
    return getBookmarksForCollection(collectionLocalId)
      .firstOrNull { collectionBookmark -> collectionBookmark.bookmarkLocalId == bookmark.localId }
      ?: error("Expected bookmark ${bookmark.localId} in collection $collectionLocalId after sync-service write.")
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
    localId: String,
    sura: Int,
    ayah: Int
  ): ReadingSession {
    return quranDataService.updateReadingSession(localId, sura, ayah)
  }

  override suspend fun updateReadingSession(
    localId: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): ReadingSession {
    return quranDataService.updateReadingSession(localId, sura, ayah, timestamp)
  }

  override fun getReadingSessionsFlow(): Flow<List<ReadingSession>> {
    return quranDataService.readingSessions
  }

  override suspend fun deleteReadingSession(sura: Int, ayah: Int): Boolean {
    return quranDataService.deleteReadingSession(sura, ayah)
  }
}
