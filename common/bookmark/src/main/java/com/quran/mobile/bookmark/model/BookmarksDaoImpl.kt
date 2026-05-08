@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quran.mobile.bookmark.model

import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.shared.persistence.QuranDatabase
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.util.fromPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.Collection as SyncCollection

@SingleIn(AppScope::class)
class BookmarksDaoImpl internal constructor(
  private val quranInfoProvider: () -> QuranInfo,
  private val bookmarksRepository: BookmarksRepository,
  private val collectionsRepository: CollectionsRepository,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  appCoroutineScope: CoroutineScope
) : BookmarksDao {
  @Inject
  constructor(
    quranInfoProvider: () -> QuranInfo,
    mobileSyncDatabase: MobileSyncDatabase,
    appCoroutineScope: AppCoroutineScope
  ) : this(
    quranInfoProvider,
    BookmarksRepositoryImpl(mobileSyncDatabase.database),
    CollectionsRepositoryImpl(mobileSyncDatabase.database),
    CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database),
    appCoroutineScope
  )

  internal constructor(
    quranInfoProvider: () -> QuranInfo,
    quranDatabase: QuranDatabase,
    appCoroutineScope: CoroutineScope = AppCoroutineScope()
  ) : this(
    quranInfoProvider,
    BookmarksRepositoryImpl(quranDatabase),
    CollectionsRepositoryImpl(quranDatabase),
    CollectionBookmarksRepositoryImpl(quranDatabase),
    appCoroutineScope
  )

  private val bookmarksState: StateFlow<List<AyahBookmark>?> =
    bookmarksRepository.getBookmarksFlow()
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  private val collectionsState: StateFlow<List<SyncCollection>?> =
    collectionsRepository.getCollectionsFlow()
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  private val bookmarkTagsState: StateFlow<Map<Long, List<Long>>?> =
    bookmarkTagsByBookmarkIdFlow()
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override val changes: Flow<Unit> =
    combine(
      bookmarksState.filterNotNull(),
      collectionsState.filterNotNull(),
      bookmarkTagsState.filterNotNull()
    ) { _, _, _ -> Unit }
      .drop(1)

  override suspend fun bookmarks(sortOrder: Int): List<Bookmark> {
    return withContext(Dispatchers.IO) {
      val tagsByBookmarkId = bookmarkTagsByBookmarkId()
      sortBookmarks(
        bookmarksRepository.getAllBookmarks().map { bookmark ->
          toBookmark(
            bookmark,
            tagIdsForBookmark(bookmark.localId, tagsByBookmarkId)
          )
        },
        sortOrder
      )
    }
  }

  override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> {
    return combine(
      bookmarksState.filterNotNull(),
      bookmarkTagsState.filterNotNull()
    ) { bookmarks, tagsByBookmarkId ->
      sortBookmarks(
        bookmarks.map { bookmark ->
          toBookmark(
            bookmark,
            tagsByBookmarkId[bookmark.localId.toLongOrNull()].orEmpty()
          )
        },
        sortOrder
      )
    }
      .distinctUntilChanged()
  }

  override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> {
    return bookmarksFlow(BookmarkSortOrder.SORT_LOCATION)
      .map { bookmarks -> bookmarks.filter { it.page == page } }
      .distinctUntilChanged()
  }

  override suspend fun tags(): List<Tag> {
    return withContext(Dispatchers.IO) {
      collectionsRepository.getAllCollections().mapNotNull(::toTag)
    }
  }

  override fun tagsFlow(): Flow<List<Tag>> {
    return collectionsState.filterNotNull()
      .map { collections -> collections.mapNotNull(::toTag) }
      .distinctUntilChanged()
  }

  override suspend fun addTag(name: String): Long {
    return withContext(Dispatchers.IO) {
      collectionsRepository.addCollection(name).localId.toLong()
    }
  }

  override suspend fun updateTag(tag: Tag): Boolean {
    return withContext(Dispatchers.IO) {
      collectionsRepository.updateCollection(tag.id.toString(), tag.name)
      true
    }
  }

  override suspend fun removeTags(tags: List<Tag>) {
    withContext(Dispatchers.IO) {
      tags
        .filter { tag -> tag.id > 0 }
        .forEach { tag -> collectionsRepository.deleteCollection(tag.id.toString()) }
    }
  }

  override suspend fun getBookmarkTagIds(bookmarkId: Long): List<Long> {
    return withContext(Dispatchers.IO) {
      bookmarkTagsByBookmarkId()[bookmarkId].orEmpty()
    }
  }

  override suspend fun getAyahBookmarkTagIds(suraAyah: SuraAyah): List<Long> {
    return withContext(Dispatchers.IO) {
      val bookmark = bookmarkForSuraAyah(suraAyah) ?: return@withContext emptyList()
      tagIdsForBookmark(bookmark.localId)
    }
  }

  override suspend fun updateBookmarkTags(
    bookmarkIds: LongArray,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean
  ): Boolean {
    return withContext(Dispatchers.IO) {
      val collectionsById = collectionsById()
      val bookmarksById = bookmarksRepository.getAllBookmarks()
        .associateBy { bookmark -> bookmark.localId.toLongOrNull() }
      bookmarkIds
        .filter { bookmarkId -> bookmarkId > 0 }
        .distinct()
        .forEach { bookmarkId ->
          val bookmark = bookmarksById[bookmarkId] ?: return@forEach
          updateBookmarkTagsInternal(bookmark, tagIds, deleteNonTagged, collectionsById)
        }
      true
    }
  }

  override suspend fun updateAyahBookmarkTags(
    suraAyah: SuraAyah,
    page: Int,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean
  ): Boolean {
    return withContext(Dispatchers.IO) {
      val collectionsById = collectionsById()
      val targetTagIds = tagIds.filter { tagId -> tagId > 0 && collectionsById.containsKey(tagId) }.toSet()
      val existingBookmark = bookmarkForSuraAyah(suraAyah)
      if (existingBookmark != null) {
        updateBookmarkTagsInternal(existingBookmark, targetTagIds, deleteNonTagged, collectionsById)
      } else if (targetTagIds.isNotEmpty()) {
        targetTagIds.forEach { tagId ->
          val collection = collectionsById[tagId] ?: return@forEach
          collectionBookmarksRepository.addAyahBookmarkToCollection(
            collectionLocalId = collection.localId,
            sura = suraAyah.sura,
            ayah = suraAyah.ayah
          )
        }
      }
      true
    }
  }

  override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: Long): Boolean {
    return withContext(Dispatchers.IO) {
      val ayahBookmark = findAyahBookmark(bookmark) ?: return@withContext false
      val collection = collectionsById()[tagId] ?: return@withContext false
      collectionBookmarksRepository.removeBookmarkFromCollection(collection.localId, ayahBookmark)
      true
    }
  }

  override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
    withContext(Dispatchers.IO) {
      bookmarks
        .filterNot { it.isPageBookmark() }
        .forEach { bookmark ->
          val ayahBookmark = findAyahBookmark(bookmark) ?: return@forEach
          removeAllTagsFromBookmark(ayahBookmark)
          bookmarksRepository.deleteBookmark(
            sura = ayahBookmark.sura,
            ayah = ayahBookmark.ayah
          )
        }
    }
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    return withContext(Dispatchers.IO) {
      bookmarkForSuraAyah(suraAyah) != null
    }
  }

  override suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val existingBookmark = bookmarkForSuraAyah(suraAyah)
      if (existingBookmark != null) {
        removeAllTagsFromBookmark(existingBookmark)
        bookmarksRepository.deleteBookmark(suraAyah.sura, suraAyah.ayah)
        false
      } else {
        bookmarksRepository.addBookmark(suraAyah.sura, suraAyah.ayah)
        true
      }
    }
  }

  private suspend fun updateBookmarkTagsInternal(
    bookmark: AyahBookmark,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean,
    collectionsById: Map<Long, SyncCollection>
  ) {
    val currentTagIds = tagIdsForBookmark(bookmark.localId).toSet()
    val targetTagIds = tagIds.filter { tagId -> tagId > 0 && collectionsById.containsKey(tagId) }.toSet()

    if (deleteNonTagged) {
      (currentTagIds - targetTagIds).forEach { tagId ->
        collectionsById[tagId]?.let { collection ->
          collectionBookmarksRepository.removeBookmarkFromCollection(collection.localId, bookmark)
        }
      }
    }

    (targetTagIds - currentTagIds).forEach { tagId ->
      collectionsById[tagId]?.let { collection ->
        collectionBookmarksRepository.addBookmarkToCollection(collection.localId, bookmark)
      }
    }
  }

  private suspend fun removeAllTagsFromBookmark(bookmark: AyahBookmark) {
    val collectionsById = collectionsById()
    tagIdsForBookmark(bookmark.localId).forEach { tagId ->
      collectionsById[tagId]?.let { collection ->
        collectionBookmarksRepository.removeBookmarkFromCollection(collection.localId, bookmark)
      }
    }
  }

  private suspend fun tagIdsForBookmark(bookmarkLocalId: String): List<Long> {
    return tagIdsForBookmark(bookmarkLocalId, bookmarkTagsByBookmarkId())
  }

  private fun tagIdsForBookmark(
    bookmarkLocalId: String,
    tagsByBookmarkId: Map<Long, List<Long>>
  ): List<Long> {
    return bookmarkLocalId.toLongOrNull()
      ?.let { bookmarkId -> tagsByBookmarkId[bookmarkId] }
      .orEmpty()
  }

  private fun bookmarkTagsByBookmarkIdFlow(): Flow<Map<Long, List<Long>>> {
    return collectionsState.filterNotNull()
      .flatMapLatest { collections ->
        if (collections.isEmpty()) {
          flowOf(emptyMap())
        } else {
          combine(
            collections.map { collection ->
              collectionBookmarksRepository.getBookmarksForCollectionFlow(collection.localId)
                .map { bookmarks ->
                  collectionBookmarksToPairs(collection, bookmarks)
                }
            }
          ) { bookmarkTags ->
            bookmarkTags
              .flatMap { it }
              .groupBy({ it.first }, { it.second })
          }
        }
      }
      .distinctUntilChanged()
  }

  private fun collectionBookmarksToPairs(
    collection: SyncCollection,
    bookmarks: List<CollectionAyahBookmark>
  ): List<Pair<Long, Long>> {
    val tagId = collection.localId.toLongOrNull() ?: return emptyList()
    return bookmarks.mapNotNull { bookmark ->
      bookmark.bookmarkLocalId.toLongOrNull()?.let { bookmarkId -> bookmarkId to tagId }
    }
  }

  private suspend fun bookmarkTagsByBookmarkId(): Map<Long, List<Long>> {
    return loadBookmarkTagsByBookmarkId(collectionsRepository.getAllCollections())
  }

  private suspend fun loadBookmarkTagsByBookmarkId(
    collections: List<SyncCollection>
  ): Map<Long, List<Long>> {
    return collections
      .flatMap { collection ->
        collectionBookmarksToPairs(
          collection,
          collectionBookmarksRepository.getBookmarksForCollection(collection.localId)
        )
      }
      .groupBy({ it.first }, { it.second })
  }

  private suspend fun collectionsById(): Map<Long, SyncCollection> {
    return collectionsRepository.getAllCollections()
      .mapNotNull { collection ->
        collection.localId.toLongOrNull()?.let { id -> id to collection }
      }
      .toMap()
  }

  private suspend fun bookmarkForSuraAyah(suraAyah: SuraAyah): AyahBookmark? {
    return bookmarksRepository.getAllBookmarks()
      .firstOrNull { bookmark -> bookmark.sura == suraAyah.sura && bookmark.ayah == suraAyah.ayah }
  }

  private suspend fun findAyahBookmark(bookmark: Bookmark): AyahBookmark? {
    val bookmarkId = bookmark.id
    val sura = bookmark.sura
    val ayah = bookmark.ayah
    return bookmarksRepository.getAllBookmarks()
      .firstOrNull { ayahBookmark -> ayahBookmark.localId.toLongOrNull() == bookmarkId }
      ?: if (sura != null && ayah != null) {
        bookmarkForSuraAyah(SuraAyah(sura, ayah))
      } else {
        null
      }
  }

  private fun toBookmark(bookmark: AyahBookmark, tagIds: List<Long>): Bookmark {
    val timestampSeconds = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
    val page = quranInfoProvider().getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
    return Bookmark(
      id = bookmark.localId.toLong(),
      sura = bookmark.sura,
      ayah = bookmark.ayah,
      page = page,
      timestamp = timestampSeconds,
      tags = tagIds
    )
  }

  private fun toTag(collection: SyncCollection): Tag? {
    return collection.localId.toLongOrNull()?.let { id -> Tag(id, collection.name) }
  }

  private fun sortBookmarks(bookmarks: List<Bookmark>, sortOrder: Int): List<Bookmark> {
    return when (sortOrder) {
      BookmarkSortOrder.SORT_LOCATION ->
        bookmarks.sortedWith(compareBy<Bookmark> { it.page }.thenBy { it.sura }.thenBy { it.ayah })
      else -> bookmarks.sortedByDescending { it.timestamp }
    }
  }
}
