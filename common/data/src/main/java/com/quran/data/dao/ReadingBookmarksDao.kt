package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import kotlinx.coroutines.flow.Flow

interface ReadingBookmarksDao {
  fun readingBookmarkFlow(): Flow<ReadingBookmark?>
  suspend fun readingBookmark(): ReadingBookmark?
  suspend fun setPageReadingBookmark(page: Int): Boolean
  suspend fun setAyahReadingBookmark(suraAyah: SuraAyah): Boolean
  suspend fun deleteReadingBookmark(): Boolean
  suspend fun isPageReadingBookmark(page: Int): Boolean
  suspend fun togglePageReadingBookmark(page: Int): Boolean
}
