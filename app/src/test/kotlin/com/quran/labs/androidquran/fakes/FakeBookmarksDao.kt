package com.quran.labs.androidquran.fakes

import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.LegacyBookmarkIds
import com.quran.data.model.bookmark.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBookmarksDao : BookmarksDao {
  private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
  private val tags = MutableStateFlow<List<Tag>>(emptyList())
  private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val addedTagNames = mutableListOf<String>()

  override val changes: Flow<Unit> = changesFlow

  fun setBookmarks(newBookmarks: List<Bookmark>) {
    bookmarks.value = newBookmarks
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

  override suspend fun tags(): List<Tag> {
    return tags.value
  }

  override fun tagsFlow(): Flow<List<Tag>> {
    return tags
  }

  override suspend fun addTag(name: String): String {
    val id = LegacyBookmarkIds.tagId((tags.value.size + 1).toLong())
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
      current.map { bookmark -> bookmark.copy(tags = bookmark.tags.filterNot { it in tagIds }) }
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
      current.map { bookmark ->
        if (bookmark.id in bookmarkIdSet) {
          val updatedTags = if (deleteNonTagged) {
            tagIds.toList()
          } else {
            (bookmark.tags + tagIds).distinct()
          }
          bookmark.copy(tags = updatedTags)
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
      val id = LegacyBookmarkIds.bookmarkId((bookmarks.value.size + 1).toLong())
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
      current.map {
        if (it.id == bookmark.id) {
          it.copy(tags = it.tags.filterNot { id -> id == tagId })
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
    changesFlow.tryEmit(Unit)
  }

  override suspend fun removeBookmarksForPage(page: Int) {
    bookmarks.update { current -> current.filterNot { it.page == page } }
    changesFlow.tryEmit(Unit)
  }

  override suspend fun replaceAyahBookmarks(bookmarks: List<Bookmark>) {
    this.bookmarks.value = bookmarks.filterNot { it.isPageBookmark() }
    changesFlow.tryEmit(Unit)
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    return bookmarks.value.any { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
  }

  override suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean {
    return if (isSuraAyahBookmarked(suraAyah)) {
      bookmarks.update { current ->
        current.filterNot { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
      }
      changesFlow.tryEmit(Unit)
      false
    } else {
      val id = LegacyBookmarkIds.bookmarkId((bookmarks.value.size + 1).toLong())
      bookmarks.update { current ->
        current + Bookmark(id, suraAyah.sura, suraAyah.ayah, page, System.currentTimeMillis() / 1000)
      }
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
}
