package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.ReadingCollectionBookmarks
import kotlinx.coroutines.flow.Flow

object BookmarkSortOrder {
  const val SORT_DATE_ADDED = 0
  const val SORT_LOCATION = 1
}

interface BookmarksDao {
  val changes: Flow<Unit>

  suspend fun bookmarks(sortOrder: Int = BookmarkSortOrder.SORT_DATE_ADDED): List<Bookmark>
  fun bookmarksFlow(sortOrder: Int = BookmarkSortOrder.SORT_DATE_ADDED): Flow<List<Bookmark>>
  fun bookmarksForPage(page: Int): Flow<List<Bookmark>>

  fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>>

  suspend fun tags(): List<Tag>
  fun tagsFlow(): Flow<List<Tag>>
  suspend fun addTag(name: String): String
  suspend fun updateTag(tag: Tag): Boolean
  suspend fun removeTags(tags: List<Tag>)

  suspend fun getBookmarkTagIds(bookmarkId: String): List<String>
  suspend fun getAyahBookmarkTagIds(suraAyah: SuraAyah): List<String>
  suspend fun updateBookmarkTags(
    bookmarkIds: Array<String>,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean
  suspend fun updateAyahBookmarkTags(
    suraAyah: SuraAyah,
    page: Int,
    tagIds: Set<String>,
    deleteNonTagged: Boolean
  ): Boolean
  suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean

  suspend fun removeBookmarks(bookmarks: List<Bookmark>)
  suspend fun removeBookmarksForPage(page: Int)
  suspend fun replaceAyahBookmarks(bookmarks: List<Bookmark>)
  suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean
  suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean
}
