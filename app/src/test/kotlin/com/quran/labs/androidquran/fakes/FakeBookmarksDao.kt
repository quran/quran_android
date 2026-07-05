package com.quran.labs.androidquran.fakes

import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.ReadingCollection
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.mobile.bookmark.model.DEFAULT_BOOKMARK_COLLECTION_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeBookmarksDao(
  private val pageForSuraAyah: (SuraAyah) -> Int = { 0 }
) : BookmarksDao {
  private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
  private val tags = MutableStateFlow<List<Tag>>(emptyList())
  private val defaultBookmarkIds = MutableStateFlow<Set<String>>(emptySet())
  private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val addedTagNames = mutableListOf<String>()

  override val changes: Flow<Unit> = changesFlow

  fun setBookmarks(newBookmarks: List<Bookmark>) {
    bookmarks.value = newBookmarks
    defaultBookmarkIds.value = newBookmarks
      .filter { bookmark -> bookmark.sura != null && bookmark.ayah != null }
      .map { bookmark -> bookmark.id }
      .toSet()
    changesFlow.tryEmit(Unit)
  }

  fun setTags(newTags: List<Tag>) {
    tags.value = newTags
    changesFlow.tryEmit(Unit)
  }

  fun currentBookmarks(): List<Bookmark> {
    return bookmarks.value
  }

  fun currentTags(): List<Tag> {
    return tags.value
  }

  fun addedTagNames(): List<String> {
    return addedTagNames.toList()
  }

  override suspend fun bookmarks(sortOrder: Int): List<Bookmark> {
    return sortBookmarks(bookmarks.value, sortOrder)
  }

  override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> {
    return bookmarks.map { sortBookmarks(it, sortOrder) }
  }

  override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> {
    return bookmarksFlow(BookmarkSortOrder.SORT_LOCATION)
      .map { bookmarks -> bookmarks.filter { it.page == page } }
  }

  override fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>> {
    return combine(tags, bookmarks, defaultBookmarkIds) { tags, bookmarks, defaultBookmarkIds ->
      val ayahBookmarks = sortBookmarks(
        bookmarks.filter { bookmark -> bookmark.sura != null && bookmark.ayah != null },
        BookmarkSortOrder.SORT_LOCATION
      )
      listOf(defaultCollection(ayahBookmarks.filter { bookmark -> bookmark.id in defaultBookmarkIds })) +
        tags.map { tag ->
          tagCollection(
            tag,
            ayahBookmarks.filter { bookmark -> tag.id in bookmark.tags }
          )
        }
    }
  }

  override suspend fun addCollection(name: String): ReadingCollection {
    val id = addTag(name)
    return ReadingCollection(
      id = id,
      name = name,
      lastUpdated = currentTimestampSeconds().toInstant(),
      isSystem = false
    )
  }

  override suspend fun tags(): List<Tag> {
    return tags.value
  }

  override fun tagsFlow(): Flow<List<Tag>> {
    return tags
  }

  override suspend fun addTag(name: String): String {
    val id = "tag-${tags.value.size + 1}"
    addedTagNames += name
    tags.update { current -> current + Tag(id, name) }
    changesFlow.tryEmit(Unit)
    return id
  }

  override suspend fun updateTag(tag: Tag): Boolean {
    tags.update { current -> current.map { if (it.id == tag.id) tag else it } }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun removeTags(tags: List<Tag>) {
    val tagIds = tags.map { it.id }.toSet()
    this.tags.update { current -> current.filterNot { it.id in tagIds } }
    bookmarks.update { current ->
      current.mapNotNull { bookmark ->
        bookmark.withUpdatedTags(bookmark.tags.filterNot { it in tagIds })
      }
    }
    changesFlow.tryEmit(Unit)
  }

  override suspend fun getBookmarkTagIds(bookmarkId: String): List<String> {
    return bookmarks.value.firstOrNull { it.id == bookmarkId }?.tags.orEmpty()
  }

  override suspend fun getAyahBookmarkTagIds(suraAyah: SuraAyah): List<String> {
    return bookmarks.value
      .firstOrNull { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
      ?.tags
      .orEmpty()
  }

  override suspend fun updateBookmarkTags(
    bookmarkIds: Array<String>,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean {
    val bookmarkIdSet = bookmarkIds.toSet()
    bookmarks.update { current ->
      current.mapNotNull { bookmark ->
        if (bookmark.id in bookmarkIdSet) {
          val updatedTags = if (deleteNonTagged) {
            tagIds.toList()
          } else {
            (bookmark.tags + tagIds).distinct()
          }
          bookmark.withUpdatedTags(updatedTags)
        } else {
          bookmark
        }
      }
    }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun updateAyahBookmarkTags(
    suraAyah: SuraAyah,
    page: Int,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean {
    val existing = bookmarks.value.firstOrNull { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
    if (existing == null && tagIds.isNotEmpty()) {
      val id = "bookmark-${bookmarks.value.size + 1}"
      bookmarks.update { current ->
        current + Bookmark(id, suraAyah.sura, suraAyah.ayah, page, System.currentTimeMillis() / 1000, tagIds.toList())
      }
    } else if (existing != null) {
      updateBookmarkTags(arrayOf(existing.id), tagIds, deleteNonTagged)
      return true
    }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean {
    bookmarks.update { current ->
      current.mapNotNull {
        if (it.id == bookmark.id) {
          it.withUpdatedTags(it.tags.filterNot { id -> id == tagId })
        } else {
          it
        }
      }
    }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun removeBookmarks(bookmarks: List<Bookmark>) {
    val bookmarkIds = bookmarks.map { it.id }.toSet()
    this.bookmarks.update { current -> current.filterNot { it.id in bookmarkIds } }
    defaultBookmarkIds.update { current -> current - bookmarkIds }
    changesFlow.tryEmit(Unit)
  }

  override suspend fun removeBookmarksForPage(page: Int) {
    val bookmarkIds = bookmarks.value
      .filter { bookmark -> bookmark.page == page }
      .map { bookmark -> bookmark.id }
      .toSet()
    bookmarks.update { current -> current.filterNot { it.page == page } }
    defaultBookmarkIds.update { current -> current - bookmarkIds }
    changesFlow.tryEmit(Unit)
  }

  override suspend fun deleteAyahBookmark(suraAyah: SuraAyah): Boolean {
    val bookmarkIds = bookmarks.value
      .filter { bookmark -> bookmark.sura == suraAyah.sura && bookmark.ayah == suraAyah.ayah }
      .map { bookmark -> bookmark.id }
      .toSet()
    if (bookmarkIds.isEmpty()) {
      return false
    }

    bookmarks.update { current -> current.filterNot { bookmark -> bookmark.id in bookmarkIds } }
    defaultBookmarkIds.update { current -> current - bookmarkIds }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun replaceAyahBookmarks(bookmarks: List<Bookmark>) {
    val ayahBookmarks = bookmarks.filter { bookmark -> bookmark.sura != null && bookmark.ayah != null }
    this.bookmarks.value = ayahBookmarks
    defaultBookmarkIds.value = ayahBookmarks.map { bookmark -> bookmark.id }.toSet()
    changesFlow.tryEmit(Unit)
  }

  override suspend fun replaceAyahBookmarkCollections(
    suraAyah: SuraAyah,
    collectionIds: Set<String>
  ): Boolean {
    val targetCollectionIds = normalizedCollectionIds(collectionIds)
    val customCollectionIds = targetCollectionIds.filterNot { collectionId ->
      collectionId == DEFAULT_BOOKMARK_COLLECTION_ID
    }.toSet()
    val isDefaultBookmark = DEFAULT_BOOKMARK_COLLECTION_ID in targetCollectionIds
    val existingBookmark = bookmarks.value.firstOrNull { bookmark ->
      bookmark.sura == suraAyah.sura && bookmark.ayah == suraAyah.ayah
    }

    if (existingBookmark == null) {
      val id = "bookmark-${bookmarks.value.size + 1}"
      bookmarks.update { current ->
        current + Bookmark(
          id = id,
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          page = pageForSuraAyah(suraAyah),
          timestamp = currentTimestampSeconds(),
          tags = customCollectionIds.toList()
        )
      }
      if (isDefaultBookmark) {
        defaultBookmarkIds.update { current -> current + id }
      }
      changesFlow.tryEmit(Unit)
      return true
    }

    val customCollectionsChanged = existingBookmark.tags.toSet() != customCollectionIds
    val defaultCollectionChanged = (existingBookmark.id in defaultBookmarkIds.value) != isDefaultBookmark
    if (!customCollectionsChanged && !defaultCollectionChanged) {
      return false
    }

    if (customCollectionsChanged) {
      bookmarks.update { current ->
        current.map { bookmark ->
          if (bookmark.id == existingBookmark.id) {
            bookmark.copy(tags = customCollectionIds.toList())
          } else {
            bookmark
          }
        }
      }
    }
    if (defaultCollectionChanged) {
      defaultBookmarkIds.update { current ->
        if (isDefaultBookmark) {
          current + existingBookmark.id
        } else {
          current - existingBookmark.id
        }
      }
    }
    changesFlow.tryEmit(Unit)
    return true
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    return bookmarks.value.any { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
  }

  override suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean {
    return if (isSuraAyahBookmarked(suraAyah)) {
      val bookmarkIds = bookmarks.value
        .filter { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
        .map { bookmark -> bookmark.id }
        .toSet()
      bookmarks.update { current ->
        current.filterNot { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
      }
      defaultBookmarkIds.update { current -> current - bookmarkIds }
      changesFlow.tryEmit(Unit)
      false
    } else {
      val id = "bookmark-${bookmarks.value.size + 1}"
      bookmarks.update { current ->
        current + Bookmark(id, suraAyah.sura, suraAyah.ayah, page, System.currentTimeMillis() / 1000)
      }
      defaultBookmarkIds.update { current -> current + id }
      changesFlow.tryEmit(Unit)
      true
    }
  }

  private fun sortBookmarks(bookmarks: List<Bookmark>, sortOrder: Int): List<Bookmark> {
    return when (sortOrder) {
      BookmarkSortOrder.SORT_LOCATION -> bookmarks.sortedWith(
        compareBy<Bookmark> { it.page }
          .thenBy { it.sura }
          .thenBy { it.ayah }
      )
      else -> bookmarks.sortedByDescending { it.timestamp }
    }
  }

  private fun Bookmark.withUpdatedTags(updatedTags: List<String>): Bookmark? {
    return if (updatedTags.isEmpty() && id !in defaultBookmarkIds.value) {
      null
    } else {
      copy(tags = updatedTags)
    }
  }

  private fun defaultCollection(bookmarks: List<Bookmark>): ReadingCollectionBookmarks {
    return ReadingCollectionBookmarks(
      readingCollection = ReadingCollection(
        id = DEFAULT_BOOKMARK_COLLECTION_ID,
        name = DEFAULT_COLLECTION_NAME,
        lastUpdated = bookmarks.lastUpdated(),
        isSystem = true
      ),
      bookmarks = bookmarks.map { bookmark -> bookmark.toAyahBookmark() }
    )
  }

  private fun tagCollection(tag: Tag, bookmarks: List<Bookmark>): ReadingCollectionBookmarks {
    return ReadingCollectionBookmarks(
      readingCollection = ReadingCollection(
        id = tag.id,
        name = tag.name,
        lastUpdated = bookmarks.lastUpdated(),
        isSystem = false
      ),
      bookmarks = bookmarks.map { bookmark -> bookmark.toAyahBookmark() }
    )
  }

  private fun Bookmark.toAyahBookmark(): AyahBookmark {
    val instant = timestamp.toInstant()
    return AyahBookmark(
      sura = checkNotNull(sura),
      ayah = checkNotNull(ayah),
      addedDate = instant,
      lastUpdated = instant
    )
  }

  private fun List<Bookmark>.lastUpdated(): Instant {
    return maxOfOrNull { bookmark -> bookmark.timestamp.toInstant() } ?: EMPTY_COLLECTION_TIMESTAMP
  }

  private fun Long.toInstant(): Instant {
    return if (this > EPOCH_SECONDS_UPPER_BOUND) {
      Instant.fromEpochMilliseconds(this)
    } else {
      Instant.fromEpochSeconds(this)
    }
  }

  private fun normalizedCollectionIds(collectionIds: Set<String>): Set<String> {
    return collectionIds
      .map { collectionId -> collectionId.trim() }
      .filter { collectionId -> collectionId.isNotEmpty() }
      .distinct()
      .ifEmpty { listOf(DEFAULT_BOOKMARK_COLLECTION_ID) }
      .toSet()
  }

  private fun currentTimestampSeconds(): Long {
    return System.currentTimeMillis() / 1000
  }

  private companion object {
    private const val DEFAULT_COLLECTION_NAME = "Default"
    private const val EPOCH_SECONDS_UPPER_BOUND = 10_000_000_000L
    private val EMPTY_COLLECTION_TIMESTAMP = Instant.fromEpochMilliseconds(0)
  }
}
