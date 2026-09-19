package com.quran.labs.androidquran.fakes

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeReadingBookmarksDao(
  vararg initialBookmarks: ReadingBookmark
) : ReadingBookmarksDao {
  private val bookmarks = MutableStateFlow(initialBookmarks.toList())
  private val timestamp = Instant.fromEpochSeconds(1)
  val toggledPages = mutableListOf<Int>()

  fun setReadingBookmark(bookmark: ReadingBookmark) {
    bookmarks.update { current ->
      (current.filterNot { it.slot == bookmark.slot } + bookmark)
        .filterNot { it is EmptyReadingBookmark }
        .sortedBy { it.slot }
    }
  }

  override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> = bookmarks

  override suspend fun readingBookmarks(): List<ReadingBookmark> = bookmarks.value

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    setReadingBookmark(PageReadingBookmark(slot, page, timestamp))
    return true
  }

  override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean {
    setReadingBookmark(AyahReadingBookmark(slot, suraAyah.sura, suraAyah.ayah, timestamp))
    return true
  }

  override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark {
    bookmarks.update { current -> current.filterNot { it.slot == slot } }
    return EmptyReadingBookmark(slot, timestamp)
  }

  override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    return bookmarks.value.any {
      it.slot == slot && it is PageReadingBookmark && it.page == page
    }
  }

  override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    toggledPages += page
    return if (isPageReadingBookmark(slot, page)) {
      clearReadingBookmark(slot)
      false
    } else {
      setPageReadingBookmark(slot, page)
      true
    }
  }

  override suspend fun updateReadingBookmarks(
    ayah: SuraAyah,
    added: List<ReadingBookmark>,
    removed: List<ReadingBookmark>
  ): Boolean {
    added.filterNot { it is EmptyReadingBookmark }.forEach(::setReadingBookmark)
    removed.forEach { clearReadingBookmark(it.slot) }
    return true
  }
}
