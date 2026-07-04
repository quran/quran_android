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
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.mobile.bookmark.sync.LocalDataChangeNotifier
import com.quran.mobile.bookmark.sync.notifyLocalDataChanged
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.fromPlatform
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import com.quran.shared.persistence.model.Collection as SyncCollection

@SingleIn(AppScope::class)
class BookmarksDaoImpl @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo,
  private val bookmarksRepository: BookmarksRepository,
  private val collectionsRepository: CollectionsRepository,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  private val bookmarkCollectionsState: BookmarkCollectionsState,
  private val localDataChangeNotifier: LocalDataChangeNotifier,
  private val timestampProvider: MobileSyncTimestampProvider,
  appCoroutineScope: AppCoroutineScope
) : BookmarksDao {
  private val bookmarksState: StateFlow<List<AyahBookmark>?> =
    bookmarksRepository.getBookmarksFlow()
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  private val bookmarkCollectionDataState: StateFlow<BookmarkCollectionData?> =
    bookmarkCollectionsState.collectionsWithBookmarks
      .filterNotNull()
      .map(::toBookmarkCollectionData)
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override val changes: Flow<Unit> =
    combine(
      bookmarksState.filterNotNull(),
      bookmarkCollectionDataState.filterNotNull()
    ) { _, _ -> Unit }
      .drop(1)

  override suspend fun bookmarks(sortOrder: Int): List<Bookmark> {
    return withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      sortBookmarks(
        bookmarksRepository.getAllBookmarks().map { bookmark ->
          toBookmark(
            bookmark,
            bookmarkCollectionData.tagsByBookmarkId[bookmark.localId].orEmpty()
          )
        },
        sortOrder
      )
    }
  }

  override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> {
    return combine(
      bookmarksState.filterNotNull(),
      bookmarkCollectionDataState.filterNotNull()
    ) { bookmarks, bookmarkCollectionData ->
      sortBookmarks(
        bookmarks.map { bookmark ->
          toBookmark(
            bookmark,
            bookmarkCollectionData.tagsByBookmarkId[bookmark.localId].orEmpty()
          )
        },
        sortOrder
      )
    }
      .distinctUntilChanged()
  }

  override fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>> {
    return bookmarkCollectionsState.collectionsWithBookmarks
      .filterNotNull()
      .map { collections -> collections.map { it.asReadingCollectionBookmarks() } }
  }

  override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> {
    return bookmarksFlow(BookmarkSortOrder.SORT_LOCATION)
      .map { bookmarks -> bookmarks.filter { it.page == page } }
      .distinctUntilChanged()
  }

  override suspend fun tags(): List<Tag> {
    return withContext(Dispatchers.IO) {
      bookmarkCollectionData().tags
    }
  }

  override fun tagsFlow(): Flow<List<Tag>> {
    return bookmarkCollectionDataState.filterNotNull()
      .map { bookmarkCollectionData -> bookmarkCollectionData.tags }
      .distinctUntilChanged()
  }

  override suspend fun addTag(name: String): String {
    val timestamp = timestampProvider.now()
    val tagId = withContext(Dispatchers.IO) {
      collectionsRepository.addCollection(name, timestamp).localId
    }
    localDataChangeNotifier.notifyLocalDataChanged()
    return tagId
  }

  override suspend fun updateTag(tag: Tag): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val localId = tag.id
      val collectionsById = bookmarkCollectionData().collectionsById
      val existingCollection = collectionsById[localId]
        ?: return@withContext false
      if (existingCollection.name == tag.name ||
        collectionsById.any { (collectionId, collection) -> collectionId != localId && collection.name == tag.name }
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
      val collectionsById = bookmarkCollectionData().collectionsById
      val tagsToRemove = tags.filter { tag -> collectionsById.containsKey(tag.id) }
      tagsToRemove.forEach { tag -> collectionsRepository.deleteCollection(tag.id) }
      tagsToRemove.isNotEmpty()
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun getBookmarkTagIds(bookmarkId: String): List<String> {
    return withContext(Dispatchers.IO) {
      bookmarkCollectionData().tagsByBookmarkId[bookmarkId].orEmpty()
    }
  }

  override suspend fun getAyahBookmarkTagIds(suraAyah: SuraAyah): List<String> {
    return withContext(Dispatchers.IO) {
      val bookmark = bookmarkForSuraAyah(suraAyah) ?: return@withContext emptyList()
      tagIdsForBookmark(bookmark.localId)
    }
  }

  override suspend fun updateBookmarkTags(
    bookmarkIds: Array<String>,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val collectionsById = bookmarkCollectionData.collectionsById
      val targetTagIds = validTagIds(tagIds, collectionsById)
      val bookmarksById = bookmarksRepository.getAllBookmarks()
        .associateBy { bookmark -> bookmark.localId }
      var didWrite = false
      bookmarkIds
        .filter { bookmarkId -> bookmarkId.isNotBlank() }
        .distinct()
        .forEach { bookmarkId ->
          val bookmark = bookmarksById[bookmarkId] ?: return@forEach
          val nextTagIds = if (deleteNonTagged) {
            targetTagIds
          } else {
            bookmarkCollectionData.tagsByBookmarkId[bookmarkId].orEmpty().toSet() + targetTagIds
          }
          didWrite = replaceBookmarkMemberships(
            bookmark = bookmark,
            tagIds = nextTagIds,
            isInDefaultCollection = bookmarkId in bookmarkCollectionData.defaultBookmarkIds,
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
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val collectionsById = bookmarkCollectionData.collectionsById
      val targetTagIds = validTagIds(tagIds, collectionsById)
      val existingBookmark = bookmarkForSuraAyah(suraAyah)
      if (existingBookmark != null) {
        val bookmarkId = existingBookmark.localId
        val nextTagIds = if (deleteNonTagged) {
          targetTagIds
        } else {
          bookmarkCollectionData.tagsByBookmarkId[bookmarkId].orEmpty().toSet() + targetTagIds
        }
        replaceBookmarkMemberships(
          bookmark = existingBookmark,
          tagIds = nextTagIds,
          isInDefaultCollection = bookmarkId in bookmarkCollectionData.defaultBookmarkIds,
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

  override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean {
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
    tagIds: Set<String>,
    isInDefaultCollection: Boolean,
    collectionsById: Map<String, SyncCollection>,
    timestamp: PlatformDateTime
  ): Boolean {
    val collectionLocalIds = buildList {
      if (isInDefaultCollection) {
        add(DEFAULT_BOOKMARK_COLLECTION_ID)
      }
      addAll(collectionLocalIds(tagIds, collectionsById))
    }
    return if (collectionLocalIds.isEmpty()) {
      bookmarksRepository.deleteBookmark(bookmark.localId)
    } else {
      bookmarksRepository.replaceBookmarkCollections(bookmark.localId, collectionLocalIds, timestamp)
    }
  }

  private suspend fun tagIdsForBookmark(bookmarkLocalId: String): List<String> {
    return bookmarkCollectionData().tagsByBookmarkId[bookmarkLocalId].orEmpty()
  }

  private suspend fun bookmarkCollectionData(): BookmarkCollectionData {
    return toBookmarkCollectionData(bookmarkCollectionsState.currentCollectionsWithBookmarks())
  }

  private fun toBookmarkCollectionData(
    collectionsWithBookmarks: List<CollectionWithAyahBookmarks>
  ): BookmarkCollectionData {
    val customCollectionsWithBookmarks = collectionsWithBookmarks
      .filterNot { collectionWithBookmarks -> collectionWithBookmarks.collection.isDefault }
    return BookmarkCollectionData(
      collectionsById = customCollectionsWithBookmarks
        .map { collectionWithBookmarks -> collectionWithBookmarks.collection }
        .associateBy { collection -> collection.localId },
      tags = customCollectionsWithBookmarks.map { collectionWithBookmarks ->
        Tag(collectionWithBookmarks.collection.localId, collectionWithBookmarks.collection.name)
      },
      tagsByBookmarkId = customCollectionsWithBookmarks
        .flatMap(::collectionBookmarksToPairs)
        .groupBy({ bookmarkTag -> bookmarkTag.first }, { bookmarkTag -> bookmarkTag.second }),
      defaultBookmarkIds = collectionsWithBookmarks
        .filter { collectionWithBookmarks -> collectionWithBookmarks.collection.isDefault }
        .flatMap { collectionWithBookmarks -> collectionWithBookmarks.bookmarks }
        .map { bookmark -> bookmark.bookmarkLocalId }
        .toSet()
    )
  }

  private fun collectionBookmarksToPairs(
    collectionWithBookmarks: CollectionWithAyahBookmarks
  ): List<Pair<String, String>> {
    val tagId = collectionWithBookmarks.collection.localId
    return collectionWithBookmarks.bookmarks.map { bookmark ->
      bookmark.bookmarkLocalId to tagId
    }
  }

  private fun validTagIds(
    tagIds: Set<String>,
    collectionsById: Map<String, SyncCollection>
  ): Set<String> {
    return tagIds.filter { tagId -> collectionsById.containsKey(tagId) }.toSet()
  }

  private fun collectionLocalIds(
    tagIds: Set<String>,
    collectionsById: Map<String, SyncCollection>
  ): List<String> {
    return tagIds.mapNotNull { tagId -> collectionsById[tagId]?.localId }
  }

  private suspend fun collectionsById(): Map<String, SyncCollection> {
    return bookmarkCollectionData().collectionsById
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
      .firstOrNull { ayahBookmark -> ayahBookmark.localId == bookmarkId }
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

  private fun toBookmark(bookmark: AyahBookmark, tagIds: List<String>): Bookmark {
    val timestampSeconds = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
    val page = quranInfoProvider().getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
    return Bookmark(
      id = bookmark.localId,
      sura = bookmark.sura,
      ayah = bookmark.ayah,
      page = page,
      timestamp = timestampSeconds,
      tags = tagIds
    )
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

  /**
   * Snapshot-derived lookup data used by legacy bookmark/tag APIs.
   *
   * [tagsByBookmarkId] intentionally excludes the default collection because `Bookmark.tags`
   * represents user-visible custom collections only. [defaultBookmarkIds] keeps the hidden default
   * membership available for write paths that need to preserve or remove the bookmark itself.
   */
  private data class BookmarkCollectionData(
    val collectionsById: Map<String, SyncCollection>,
    val tags: List<Tag>,
    val tagsByBookmarkId: Map<String, List<String>>,
    val defaultBookmarkIds: Set<String>
  )

  private companion object {
    private const val QURAN_SURA_COUNT = 114
  }
}
