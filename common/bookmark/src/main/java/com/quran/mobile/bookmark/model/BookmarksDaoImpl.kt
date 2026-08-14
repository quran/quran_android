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
import com.quran.data.model.collection.ReadingCollection
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahBookmark
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepository
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepository
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.fromPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
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
  private val bookmarkCollectionsState: BookmarkCollectionsState,
  private val timestampProvider: MobileSyncTimestampProvider,
  appCoroutineScope: AppCoroutineScope
) : BookmarksDao {
  private val bookmarkCollectionDataState: StateFlow<BookmarkCollectionData?> =
    bookmarkCollectionsState.collectionsWithBookmarks
      .filterNotNull()
      .map(::toBookmarkCollectionData)
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override val changes: Flow<Unit> = bookmarkCollectionDataState
    .filterNotNull()
    .map { }
    .drop(1)

  override suspend fun bookmarks(sortOrder: Int): List<Bookmark> {
    return withContext(Dispatchers.IO) {
      sortBookmarks(bookmarkCollectionData().bookmarks, sortOrder)
    }
  }

  override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> {
    return bookmarkCollectionDataState
      .filterNotNull()
      .map { bookmarkCollectionData -> sortBookmarks(bookmarkCollectionData.bookmarks, sortOrder) }
      .distinctUntilChanged()
  }

  override fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>> {
    return bookmarkCollectionsState.collectionsWithBookmarks
      .filterNotNull()
      .map { collections -> collections.map { it.asReadingCollectionBookmarks() } }
  }

  override suspend fun addCollection(name: String): ReadingCollection {
    return withContext(Dispatchers.IO) {
      collectionsRepository.addCollection(name)
        .asReadingCollection()
    }
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
      collectionsRepository.addCollection(name, timestamp).id
    }
    return tagId
  }

  override suspend fun updateTag(tag: Tag): Boolean {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val localId = tag.id
      val collectionsById = bookmarkCollectionData().collectionsById
      val existingCollection = collectionsById[localId]
        ?: return@withContext false
      if (existingCollection.name == tag.name) {
        return@withContext true
      }
      if (collectionsById.any { (collectionId, collection) ->
          collectionId != localId && collection.name == tag.name
        }
      ) {
        return@withContext false
      }
      try {
        collectionsRepository.updateCollection(localId, tag.name, timestamp)
        true
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        false
      }
    }
    return updated
  }

  override suspend fun removeTags(tags: List<Tag>) {
    withContext(Dispatchers.IO) {
      val collectionsById = bookmarkCollectionData().collectionsById
      val tagsToRemove = tags.filter { tag -> collectionsById.containsKey(tag.id) }
      tagsToRemove.forEach { tag -> collectionsRepository.deleteCollection(tag.id) }
    }
  }

  override suspend fun getBookmarkTagIds(bookmarkId: String): List<String> {
    return withContext(Dispatchers.IO) {
      bookmarkCollectionData().tagIds(bookmarkId)
    }
  }

  override suspend fun updateBookmarkTags(
    bookmarkIds: Array<String>,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean {
    val timestamp = timestampProvider.now()
    withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val collectionsById = bookmarkCollectionData.collectionsById
      val targetTagIds = validTagIds(tagIds, collectionsById)
      bookmarkIds
        .filter { bookmarkId -> bookmarkId.isNotBlank() }
        .distinct()
        .forEach { bookmarkId ->
          val bookmark = bookmarkCollectionData.ayahBookmarksById[bookmarkId] ?: return@forEach
          val currentCollectionIds = bookmarkCollectionData
            .collectionIdsByBookmarkId[bookmarkId]
            .orEmpty()
          val nextTagIds = if (deleteNonTagged) {
            targetTagIds
          } else {
            bookmarkCollectionData.tagIds(bookmarkId).toSet() + targetTagIds
          }
          val targetCollectionIds = buildSet {
            bookmarkCollectionData.defaultCollectionId
              ?.takeIf(currentCollectionIds::contains)
              ?.let(::add)
            addAll(nextTagIds)
          }
          replaceBookmarkCollectionIds(
            bookmark = bookmark,
            currentCollectionIds = currentCollectionIds,
            targetCollectionIds = targetCollectionIds,
            timestamp = timestamp
          )
        }
    }
    return true
  }

  override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean {
    return withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val ayahBookmark = bookmarkCollectionData.ayahBookmark(bookmark)
      val currentCollectionIds = ayahBookmark
        ?.let { bookmarkCollectionData.collectionIdsByBookmarkId[it.id] }
        .orEmpty()
      if (
        ayahBookmark == null ||
        !bookmarkCollectionData.collectionsById.containsKey(tagId) ||
        tagId !in currentCollectionIds
      ) {
        false
      } else {
        collectionBookmarksRepository.removeBookmarkFromCollection(tagId, ayahBookmark)
      }
    }
  }

  override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
    val timestamp = timestampProvider.now()
    withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      bookmarks
        .filterNot { it.isPageBookmark() }
        .forEach { bookmark ->
          val ayahBookmark = bookmarkCollectionData.ayahBookmark(bookmark) ?: return@forEach
          replaceBookmarkCollectionIds(
            bookmark = ayahBookmark,
            currentCollectionIds = bookmarkCollectionData
              .collectionIdsByBookmarkId[ayahBookmark.id]
              .orEmpty(),
            targetCollectionIds = emptySet(),
            timestamp = timestamp
          )
        }
    }
  }

  override suspend fun deleteAyahBookmark(suraAyah: SuraAyah): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val bookmark = bookmarkCollectionData.ayahBookmark(suraAyah)
      if (bookmark == null) {
        false
      } else {
        replaceBookmarkCollectionIds(
          bookmark = bookmark,
          currentCollectionIds = bookmarkCollectionData
            .collectionIdsByBookmarkId[bookmark.id]
            .orEmpty(),
          targetCollectionIds = emptySet(),
          timestamp = timestamp
        )
      }
    }
  }

  override suspend fun replaceAyahBookmarkCollections(
    suraAyah: SuraAyah,
    collectionIds: Set<String>
  ): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val bookmarkCollectionData = bookmarkCollectionData()
      val bookmarkCollectionIds = bookmarkCollectionData.bookmarkCollectionIds()
      if (!bookmarkCollectionIds.containsAll(collectionIds)) {
        false
      } else {
        val existingBookmark = bookmarkCollectionData.ayahBookmark(suraAyah)
        if (existingBookmark == null) {
          if (collectionIds.isEmpty()) {
            false
          } else {
            bookmarksRepository.addBookmark(
              sura = suraAyah.sura,
              ayah = suraAyah.ayah,
              collectionIds = collectionIds.toList(),
              timestamp = timestamp
            )
            true
          }
        } else {
          replaceBookmarkCollectionIds(
            bookmark = existingBookmark,
            currentCollectionIds = bookmarkCollectionData
              .collectionIdsByBookmarkId[existingBookmark.id]
              .orEmpty(),
            targetCollectionIds = collectionIds,
            timestamp = timestamp
          )
        }
      }
    }
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    return withContext(Dispatchers.IO) {
      bookmarkCollectionData().ayahBookmark(suraAyah) != null
    }
  }

  private suspend fun replaceBookmarkCollectionIds(
    bookmark: AyahBookmark,
    currentCollectionIds: Set<String>,
    targetCollectionIds: Set<String>,
    timestamp: PlatformDateTime
  ): Boolean {
    return if (currentCollectionIds == targetCollectionIds) {
      false
    } else {
      (targetCollectionIds - currentCollectionIds).forEach { collectionId ->
        collectionBookmarksRepository.addBookmarkToCollection(collectionId, bookmark, timestamp)
      }
      (currentCollectionIds - targetCollectionIds).forEach { collectionId ->
        collectionBookmarksRepository.removeBookmarkFromCollection(collectionId, bookmark)
      }
      true
    }
  }

  private suspend fun bookmarkCollectionData(): BookmarkCollectionData {
    return toBookmarkCollectionData(bookmarkCollectionsState.currentCollectionsWithBookmarks())
  }

  private fun toBookmarkCollectionData(
    collectionsWithBookmarks: List<CollectionWithAyahBookmarks>
  ): BookmarkCollectionData {
    val editableCollectionsWithBookmarks = collectionsWithBookmarks
      .filterNot { collectionWithBookmarks -> collectionWithBookmarks.collection.isDefault }
    val defaultCollectionWithBookmarks = collectionsWithBookmarks
      .firstOrNull { collectionWithBookmarks -> collectionWithBookmarks.collection.isDefault }
    val defaultCollectionId = defaultCollectionWithBookmarks?.collection?.id
    val membershipsByBookmarkId = collectionsWithBookmarks
      .flatMap { collectionWithBookmarks -> collectionWithBookmarks.bookmarks }
      .groupBy { bookmark -> bookmark.bookmarkId }
    val collectionIdsByBookmarkId = membershipsByBookmarkId.mapValues { (_, memberships) ->
      memberships.mapTo(mutableSetOf()) { membership -> membership.collectionId }
    }
    val representativeMembershipsByBookmarkId = membershipsByBookmarkId.mapValues { (_, memberships) ->
      memberships.maxBy { membership ->
        membership.bookmarkLastUpdated.fromPlatform().toEpochMilliseconds()
      }
    }
    val ayahBookmarksById = representativeMembershipsByBookmarkId
      .mapValues { (_, membership) -> membership.asAyahBookmark() }
    val bookmarks = representativeMembershipsByBookmarkId
      .map { (bookmarkId, bookmark) ->
        val tagIds = collectionIdsByBookmarkId[bookmarkId]
          .orEmpty()
          .filterNot { collectionId -> collectionId == defaultCollectionId }
        toBookmark(bookmark, tagIds)
      }
    return BookmarkCollectionData(
      bookmarks = bookmarks,
      ayahBookmarksById = ayahBookmarksById,
      collectionIdsByBookmarkId = collectionIdsByBookmarkId,
      collectionsById = editableCollectionsWithBookmarks
        .map { collectionWithBookmarks -> collectionWithBookmarks.collection }
        .associateBy { collection -> collection.id },
      tags = editableCollectionsWithBookmarks.map { collectionWithBookmarks ->
        Tag(collectionWithBookmarks.collection.id, collectionWithBookmarks.collection.name)
      },
      defaultCollectionId = defaultCollectionId
    )
  }

  private fun validTagIds(
    tagIds: Set<String>,
    collectionsById: Map<String, SyncCollection>
  ): Set<String> {
    return tagIds.filter { tagId -> collectionsById.containsKey(tagId) }.toSet()
  }

  private fun BookmarkCollectionData.bookmarkCollectionIds(): Set<String> {
    return buildSet {
      defaultCollectionId?.let(::add)
      addAll(collectionsById.keys)
    }
  }

  private fun BookmarkCollectionData.ayahBookmark(suraAyah: SuraAyah): AyahBookmark? {
    return ayahBookmarksById.values.firstOrNull { bookmark ->
      bookmark.sura == suraAyah.sura && bookmark.ayah == suraAyah.ayah
    }
  }

  private fun BookmarkCollectionData.ayahBookmark(bookmark: Bookmark): AyahBookmark? {
    return ayahBookmarksById[bookmark.id]
      ?: ayahBookmarksById.values.firstOrNull { ayahBookmark ->
        ayahBookmark.sura == bookmark.sura && ayahBookmark.ayah == bookmark.ayah
      }
  }

  private fun BookmarkCollectionData.tagIds(bookmarkId: String): List<String> {
    return collectionIdsByBookmarkId[bookmarkId]
      .orEmpty()
      .filterNot { collectionId -> collectionId == defaultCollectionId }
  }

  private fun CollectionAyahBookmark.asAyahBookmark(): AyahBookmark {
    return AyahBookmark(
      sura = sura,
      ayah = ayah,
      id = bookmarkId,
      lastUpdated = bookmarkLastUpdated,
      addedDate = bookmarkAddedDate
    )
  }

  private fun toBookmark(bookmark: CollectionAyahBookmark, tagIds: List<String>): Bookmark {
    val timestampSeconds = bookmark.bookmarkLastUpdated.fromPlatform().toEpochMilliseconds() / 1000
    val page = quranInfoProvider().getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
    return Bookmark(
      id = bookmark.bookmarkId,
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

  private data class BookmarkCollectionData(
    val bookmarks: List<Bookmark>,
    val ayahBookmarksById: Map<String, AyahBookmark>,
    val collectionIdsByBookmarkId: Map<String, Set<String>>,
    val collectionsById: Map<String, SyncCollection>,
    val tags: List<Tag>,
    val defaultCollectionId: String?
  )
}
