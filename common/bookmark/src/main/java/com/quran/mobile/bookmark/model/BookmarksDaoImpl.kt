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
import com.quran.mobile.bookmark.sync.LocalDataChangeNotifier
import com.quran.mobile.bookmark.sync.notifyLocalDataChanged
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.model.DEFAULT_COLLECTION_ID
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.fromPlatform
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
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
class BookmarksDaoImpl @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo,
  private val bookmarksRepository: BookmarksRepository,
  private val collectionsRepository: CollectionsRepository,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  private val localDataChangeNotifier: LocalDataChangeNotifier,
  private val timestampProvider: MobileSyncTimestampProvider,
  appCoroutineScope: AppCoroutineScope
) : BookmarksDao {
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
    val timestamp = timestampProvider.now()
    val tagId = withContext(Dispatchers.IO) {
      collectionsRepository.addCollection(name, timestamp).localId.toLong()
    }
    localDataChangeNotifier.notifyLocalDataChanged()
    return tagId
  }

  override suspend fun updateTag(tag: Tag): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val localId = tag.id.toString()
      val collections = collectionsRepository.getAllCollections()
      val existingCollection = collections.firstOrNull { collection -> collection.localId == localId }
        ?: return@withContext false
      if (existingCollection.name == tag.name ||
        collections.any { collection -> collection.localId != localId && collection.name == tag.name }
      ) {
        return@withContext false
      }
      try {
        collectionsRepository.updateCollection(localId, tag.name, timestamp)
        true
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        false
      }
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
    return updated
  }

  override suspend fun removeTags(tags: List<Tag>) {
    val removed = withContext(Dispatchers.IO) {
      val tagsToRemove = tags.filter { tag -> tag.id > 0 }
      tagsToRemove.forEach { tag -> collectionsRepository.deleteCollection(tag.id.toString()) }
      tagsToRemove.isNotEmpty()
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
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
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val collectionsById = collectionsById()
      val targetTagIds = validTagIds(tagIds, collectionsById)
      val currentTagsByBookmarkId = bookmarkTagsByBookmarkId()
      val defaultBookmarkIds = defaultBookmarkIds()
      val bookmarksById = bookmarksRepository.getAllBookmarks()
        .associateBy { bookmark -> bookmark.localId.toLongOrNull() }
      var didWrite = false
      bookmarkIds
        .filter { bookmarkId -> bookmarkId > 0 }
        .distinct()
        .forEach { bookmarkId ->
          val bookmark = bookmarksById[bookmarkId] ?: return@forEach
          val nextTagIds = if (deleteNonTagged) {
            targetTagIds
          } else {
            currentTagsByBookmarkId[bookmarkId].orEmpty().toSet() + targetTagIds
          }
          didWrite = replaceBookmarkMemberships(
            bookmark = bookmark,
            tagIds = nextTagIds,
            isInDefaultCollection = bookmarkId in defaultBookmarkIds,
            collectionsById = collectionsById,
            timestamp = timestamp
          ) ||
            didWrite
        }
      didWrite
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
    return true
  }

  override suspend fun updateAyahBookmarkTags(
    suraAyah: SuraAyah,
    page: Int,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean
  ): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val collectionsById = collectionsById()
      val targetTagIds = validTagIds(tagIds, collectionsById)
      val existingBookmark = bookmarkForSuraAyah(suraAyah)
      if (existingBookmark != null) {
        val bookmarkId = existingBookmark.localId.toLongOrNull() ?: return@withContext false
        val nextTagIds = if (deleteNonTagged) {
          targetTagIds
        } else {
          bookmarkTagsByBookmarkId()[bookmarkId].orEmpty().toSet() + targetTagIds
        }
        replaceBookmarkMemberships(
          bookmark = existingBookmark,
          tagIds = nextTagIds,
          isInDefaultCollection = bookmarkId in defaultBookmarkIds(),
          collectionsById = collectionsById,
          timestamp = timestamp
        )
      } else if (targetTagIds.isNotEmpty()) {
        bookmarksRepository.replaceAyahBookmarkCollections(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          collectionLocalIds = collectionLocalIds(targetTagIds, collectionsById),
          timestamp = timestamp
        ).changed
      } else {
        false
      }
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
    return true
  }

  override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: Long): Boolean {
    val removed = withContext(Dispatchers.IO) {
      val ayahBookmark = findAyahBookmark(bookmark) ?: return@withContext false
      val collection = collectionsById()[tagId] ?: return@withContext false
      collectionBookmarksRepository.removeBookmarkFromCollection(collection.localId, ayahBookmark)
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
    return removed
  }

  override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
    val removed = withContext(Dispatchers.IO) {
      var didWrite = false
      bookmarks
        .filterNot { it.isPageBookmark() }
        .forEach { bookmark ->
          val ayahBookmark = findAyahBookmark(bookmark) ?: return@forEach
          bookmarksRepository.deleteBookmark(ayahBookmark.localId)
          didWrite = true
        }
      didWrite
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun removeBookmarksForPage(page: Int) {
    val removed = withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      val bookmarksToRemove = bookmarksRepository.getAllBookmarks()
        .filter { bookmark ->
          quranInfo.getPageFromSuraAyah(bookmark.sura, bookmark.ayah) == page
        }
      bookmarksToRemove.forEach { bookmark ->
        bookmarksRepository.deleteBookmark(bookmark.localId)
      }
      bookmarksToRemove.isNotEmpty()
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun replaceAyahBookmarks(bookmarks: List<Bookmark>) {
    val replaced = withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      val ayahBookmarks = bookmarks.normalizedAyahBookmarks(quranInfo)
      bookmarksRepository.getAllBookmarks().forEach { bookmark ->
        bookmarksRepository.deleteBookmark(bookmark.localId)
      }
      val collectionsById = collectionsById()
      ayahBookmarks.forEach { bookmark ->
        val sura = bookmark.sura ?: return@forEach
        val ayah = bookmark.ayah ?: return@forEach
        val timestamp = bookmark.timestamp.toPlatformDateTime()
        val ayahBookmark = bookmarksRepository.addBookmark(sura, ayah, timestamp)
        bookmark.tags.forEach { tagId ->
          collectionsById[tagId]?.let { collection ->
            collectionBookmarksRepository.addBookmarkToCollection(collection.localId, ayahBookmark, timestamp)
          }
        }
      }
      true
    }
    if (replaced) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    return withContext(Dispatchers.IO) {
      bookmarkForSuraAyah(suraAyah) != null
    }
  }

  override suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean {
    val timestamp = timestampProvider.now()
    val bookmarked = withContext(Dispatchers.IO) {
      val existingBookmark = bookmarkForSuraAyah(suraAyah)
      if (existingBookmark != null) {
        bookmarksRepository.deleteBookmark(existingBookmark.localId)
        false
      } else {
        bookmarksRepository.addBookmark(suraAyah.sura, suraAyah.ayah, timestamp)
        true
      }
    }
    localDataChangeNotifier.notifyLocalDataChanged()
    return bookmarked
  }

  private suspend fun replaceBookmarkMemberships(
    bookmark: AyahBookmark,
    tagIds: Set<Long>,
    isInDefaultCollection: Boolean,
    collectionsById: Map<Long, SyncCollection>,
    timestamp: PlatformDateTime
  ): Boolean {
    val collectionLocalIds = buildList {
      if (isInDefaultCollection) {
        add(DEFAULT_COLLECTION_ID)
      }
      addAll(collectionLocalIds(tagIds, collectionsById))
    }
    return if (collectionLocalIds.isEmpty()) {
      bookmarksRepository.deleteBookmark(bookmark.localId)
    } else {
      bookmarksRepository.replaceBookmarkCollections(bookmark.localId, collectionLocalIds, timestamp)
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

  private suspend fun defaultBookmarkIds(): Set<Long> {
    return collectionBookmarksRepository.getBookmarksForCollection(DEFAULT_COLLECTION_ID)
      .mapNotNull { bookmark -> bookmark.bookmarkLocalId.toLongOrNull() }
      .toSet()
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

  private fun validTagIds(
    tagIds: Set<Long>,
    collectionsById: Map<Long, SyncCollection>
  ): Set<Long> {
    return tagIds.filter { tagId -> tagId > 0 && collectionsById.containsKey(tagId) }.toSet()
  }

  private fun collectionLocalIds(
    tagIds: Set<Long>,
    collectionsById: Map<Long, SyncCollection>
  ): List<String> {
    return tagIds.mapNotNull { tagId -> collectionsById[tagId]?.localId }
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

  private fun List<Bookmark>.normalizedAyahBookmarks(quranInfo: QuranInfo): List<Bookmark> {
    return filter { bookmark ->
      val sura = bookmark.sura
      val ayah = bookmark.ayah
      sura != null &&
        sura in 1..QURAN_SURA_COUNT &&
        ayah != null &&
        ayah in 1..quranInfo.getNumberOfAyahs(sura)
    }
      .groupBy { bookmark -> SuraAyah(bookmark.sura!!, bookmark.ayah!!) }
      .map { (_, bookmarks) ->
        val latestBookmark = bookmarks.maxBy { bookmark -> bookmark.timestamp }
        latestBookmark.copy(
          tags = bookmarks.flatMap { bookmark -> bookmark.tags }.distinct()
        )
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

  private fun Long.toPlatformDateTime(): PlatformDateTime {
    return Instant.fromEpochSeconds(this).toPlatform()
  }

  private companion object {
    private const val QURAN_SURA_COUNT = 114
  }
}
