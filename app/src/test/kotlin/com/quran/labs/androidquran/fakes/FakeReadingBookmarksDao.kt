package com.quran.labs.androidquran.fakes

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReadingBookmarksDao(
  initialBookmark: ReadingBookmark? = null
) : ReadingBookmarksDao {
  private val readingBookmark = MutableStateFlow(initialBookmark)
  val toggledPages = mutableListOf<Int>()

  fun setReadingBookmark(bookmark: ReadingBookmark?) {
    readingBookmark.value = bookmark
  }

  override fun readingBookmarkFlow(): Flow<ReadingBookmark?> {
    return readingBookmark
  }

  override suspend fun readingBookmark(): ReadingBookmark? {
    return readingBookmark.value
  }

  override suspend fun setPageReadingBookmark(page: Int): Boolean {
    readingBookmark.value = PageReadingBookmark(page, timestamp = 1)
    return true
  }

  override suspend fun setAyahReadingBookmark(suraAyah: SuraAyah): Boolean {
    readingBookmark.value = AyahReadingBookmark(
      sura = suraAyah.sura,
      ayah = suraAyah.ayah,
      timestamp = 1
    )
    return true
  }

  override suspend fun deleteReadingBookmark(): Boolean {
    readingBookmark.value = null
    return true
  }

  override suspend fun isPageReadingBookmark(page: Int): Boolean {
    val bookmark = readingBookmark.value
    return bookmark is PageReadingBookmark && bookmark.page == page
  }

  override suspend fun togglePageReadingBookmark(page: Int): Boolean {
    toggledPages += page
    return if (isPageReadingBookmark(page)) {
      readingBookmark.value = null
      false
    } else {
      readingBookmark.value = PageReadingBookmark(page, timestamp = 1)
      true
    }
  }
}
