package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import kotlinx.coroutines.flow.Flow

interface ReadingBookmarksDao {
  fun readingBookmarksFlow(): Flow<List<ReadingBookmark>>
  suspend fun readingBookmarks(): List<ReadingBookmark>
  suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean
  suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean
  suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark
  suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?)
  suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean
  suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean
  suspend fun updateReadingBookmarks(
    ayah: SuraAyah,
    added: List<ReadingBookmark>,
    removed: List<ReadingBookmark>
  ): Boolean
}
