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
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.ReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class QuranSyncRepositoryProvider(
  syncManager: QuranSyncManager,
  appCoroutineScope: AppCoroutineScope
) : MobileSyncRepositoryProvider {
  private val quranDataServiceProvider: () -> QuranDataService = { syncManager.quranDataService }

  override val bookmarksRepository: BookmarksRepository =
    SyncBookmarksRepository(quranDataServiceProvider)

  override val bookmarkCollectionsState: BookmarkCollectionsState =
    SyncBookmarkCollectionsState(quranDataServiceProvider, appCoroutineScope)

  override val collectionsRepository: CollectionsRepository =
    SyncCollectionsRepository(quranDataServiceProvider, bookmarkCollectionsState)

  override val collectionBookmarksRepository: CollectionBookmarksRepository =
    SyncCollectionBookmarksRepository(quranDataServiceProvider, bookmarkCollectionsState)

  override val readingBookmarksRepository: ReadingBookmarksRepository =
    SyncReadingBookmarksRepository(quranDataServiceProvider)

  override val readingSessionsRepository: ReadingSessionsRepository =
    SyncReadingSessionsRepository(quranDataServiceProvider)
}

private class SyncBookmarksRepository(
  private val quranDataServiceProvider: () -> QuranDataService
) : BookmarksRepository {
  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>
  ): BookmarkCollectionsReplacementResult {
    return quranDataServiceProvider().replaceAyahBookmarkCollections(sura, ayah, collectionIds)
  }

  override suspend fun replaceAyahBookmarkCollections(
    sura: Int,
    ayah: Int,
    collectionIds: List<String>,
    timestamp: PlatformDateTime
  ): BookmarkCollectionsReplacementResult {
    return quranDataServiceProvider().replaceAyahBookmarkCollections(sura, ayah, collectionIds, timestamp)
  }
}

private class SyncBookmarkCollectionsState(
  private val quranDataServiceProvider: () -> QuranDataService,
  private val appCoroutineScope: AppCoroutineScope
) : BookmarkCollectionsState {
  override val collectionsWithBookmarks: StateFlow<List<CollectionWithAyahBookmarks>?> by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED
  ) {
    flow { emitAll(quranDataServiceProvider().collectionsWithBookmarks) }
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)
  }
}

private class SyncCollectionsRepository(
  private val quranDataServiceProvider: () -> QuranDataService,
  private val bookmarkCollectionsState: BookmarkCollectionsState
) : CollectionsRepository {
  override suspend fun getAllCollections(): List<Collection> {
    return bookmarkCollectionsState.currentCollectionsWithBookmarks().map { it.collection }
  }

  override suspend fun addCollection(name: String): Collection {
    return quranDataServiceProvider().addCollection(name)
  }

  override suspend fun addCollection(name: String, timestamp: PlatformDateTime): Collection {
    return quranDataServiceProvider().addCollection(name, timestamp)
  }

  override suspend fun updateCollection(id: String, name: String): Collection {
    return quranDataServiceProvider().updateCollection(id, name)
  }

  override suspend fun updateCollection(
    id: String,
    name: String,
    timestamp: PlatformDateTime
  ): Collection {
    return quranDataServiceProvider().updateCollection(id, name, timestamp)
  }

  override suspend fun deleteCollection(id: String): Boolean {
    return quranDataServiceProvider().deleteCollection(id)
  }

  override fun getCollectionsFlow(): Flow<List<Collection>> {
    return bookmarkCollectionsState.collectionsWithBookmarks.filterNotNull().map { collections ->
      collections.map { it.collection }
    }
  }
}

private class SyncCollectionBookmarksRepository(
  private val quranDataServiceProvider: () -> QuranDataService,
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
    return quranDataServiceProvider().addAyahBookmarkToCollection(collectionId, sura, ayah)
  }

  override suspend fun addAyahBookmarkToCollection(
    collectionId: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): CollectionAyahBookmark {
    return quranDataServiceProvider().addAyahBookmarkToCollection(collectionId, sura, ayah, timestamp)
  }

  override suspend fun removeAyahBookmarkFromCollection(
    collectionId: String,
    bookmarkId: String
  ): Boolean {
    return quranDataServiceProvider().removeAyahBookmarkFromCollection(collectionId, bookmarkId)
  }

  override suspend fun removeAllAyahBookmarksFromCollection(collectionId: String): Int {
    return quranDataServiceProvider().removeAllAyahBookmarksFromCollection(collectionId)
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
    return flow { emitAll(quranDataServiceProvider().highlights) }
  }

  override suspend fun setHighlight(
    sura: Int,
    ayah: Int,
    color: AyahHighlightColor,
    timestamp: PlatformDateTime
  ): AyahHighlight {
    return quranDataServiceProvider().setHighlight(sura, ayah, color, timestamp)
  }

  override suspend fun deleteHighlight(
    sura: Int,
    ayah: Int
  ): Boolean {
    return quranDataServiceProvider().deleteHighlight(sura, ayah)
  }

  override suspend fun deleteHighlight(
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): Boolean {
    return quranDataServiceProvider().deleteHighlight(sura, ayah)
  }

  override suspend fun deleteHighlights(color: AyahHighlightColor): Int {
    return quranDataServiceProvider().deleteHighlights(color)
  }
}

private class SyncReadingBookmarksRepository(
  private val quranDataServiceProvider: () -> QuranDataService
) : ReadingBookmarksRepository {
  override suspend fun getReadingBookmarks(mushaf: Mushaf): List<ReadingBookmark> {
    return quranDataServiceProvider().readingBookmarks(mushaf).first()
  }

  override fun getReadingBookmarksFlow(mushaf: Mushaf): Flow<List<ReadingBookmark>> {
    return flow { emitAll(quranDataServiceProvider().readingBookmarks(mushaf)) }
  }

  override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkSlot, sura: Int, ayah: Int) =
    quranDataServiceProvider().setAyahReadingBookmark(slot, sura, ayah)

  override suspend fun setAyahReadingBookmark(
    slot: ReadingBookmarkSlot,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ) = quranDataServiceProvider().setAyahReadingBookmark(slot, sura, ayah, timestamp)

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkSlot, page: Int, mushaf: Mushaf) =
    quranDataServiceProvider().setPageReadingBookmark(slot, page, mushaf)

  override suspend fun setPageReadingBookmark(
    slot: ReadingBookmarkSlot,
    page: Int,
    mushaf: Mushaf,
    timestamp: PlatformDateTime
  ) = quranDataServiceProvider().setPageReadingBookmark(slot, page, mushaf, timestamp)

  override suspend fun clearReadingBookmark(slot: ReadingBookmarkSlot): ReadingBookmark {
    return quranDataServiceProvider().clearReadingBookmark(slot)
  }

  override suspend fun clearReadingBookmark(
    slot: ReadingBookmarkSlot,
    timestamp: PlatformDateTime
  ): ReadingBookmark {
    return quranDataServiceProvider().clearReadingBookmark(slot, timestamp)
  }

  override suspend fun renameReadingBookmark(
    slot: ReadingBookmarkSlot,
    name: String?,
    mushaf: Mushaf
  ): ReadingBookmark {
    return quranDataServiceProvider().renameReadingBookmark(slot, name, mushaf)
  }

  override suspend fun renameReadingBookmark(
    slot: ReadingBookmarkSlot,
    name: String?,
    mushaf: Mushaf,
    timestamp: PlatformDateTime
  ): ReadingBookmark {
    return quranDataServiceProvider().renameReadingBookmark(slot, name, mushaf, timestamp)
  }
}

private class SyncReadingSessionsRepository(
  private val quranDataServiceProvider: () -> QuranDataService
) : ReadingSessionsRepository {
  override suspend fun getReadingSessions(): List<ReadingSession> {
    return quranDataServiceProvider().readingSessions.first()
  }

  override suspend fun addReadingSession(sura: Int, ayah: Int): ReadingSession {
    return quranDataServiceProvider().addReadingSession(sura, ayah)
  }

  override suspend fun addReadingSession(
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): ReadingSession {
    return quranDataServiceProvider().addReadingSession(sura, ayah, timestamp)
  }

  override suspend fun updateReadingSession(
    id: String,
    sura: Int,
    ayah: Int
  ): ReadingSession {
    return quranDataServiceProvider().updateReadingSession(id, sura, ayah)
  }

  override suspend fun updateReadingSession(
    id: String,
    sura: Int,
    ayah: Int,
    timestamp: PlatformDateTime
  ): ReadingSession {
    return quranDataServiceProvider().updateReadingSession(id, sura, ayah, timestamp)
  }

  override fun getReadingSessionsFlow(): Flow<List<ReadingSession>> {
    return flow { emitAll(quranDataServiceProvider().readingSessions) }
  }

  override suspend fun deleteReadingSession(sura: Int, ayah: Int): Boolean {
    return quranDataServiceProvider().deleteReadingSession(sura, ayah)
  }
}
