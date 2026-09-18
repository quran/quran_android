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
  initialBookmark: ReadingBookmark? = null
) : ReadingBookmarksDao {
  private val readingBookmarks = MutableStateFlow(listOfNotNull(initialBookmark))
  val toggledPages = mutableListOf<Int>()

  fun setReadingBookmark(bookmark: ReadingBookmark) {
    readingBookmarks.update { bookmarks ->
      (bookmarks.filterNot { it.slot == bookmark.slot } + bookmark)
        .filterNot { it is EmptyReadingBookmark }
        .sortedBy { it.slot.ordinal }
    }
  }

  override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> {
    return readingBookmarks
  }

  override suspend fun readingBookmarks(): List<ReadingBookmark> {
    return readingBookmarks.value
  }

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    setReadingBookmark(PageReadingBookmark(slot, page, timestamp = Instant.fromEpochSeconds(1)))
    return true
  }

  override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean {
    setReadingBookmark(
      AyahReadingBookmark(
        slot = slot,
        sura = suraAyah.sura,
        ayah = suraAyah.ayah,
        timestamp = Instant.fromEpochSeconds(1)
      )
    )
    return true
  }

  override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark {
    val previous = readingBookmarks.value.firstOrNull { it.slot == slot }
    readingBookmarks.update { bookmarks -> bookmarks.filterNot { it.slot == slot } }
    return previous ?: EmptyReadingBookmark(slot, Instant.fromEpochSeconds(1))
  }

  override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    return readingBookmarks.value.any {
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
    }
  }

  override suspend fun updateReadingBookmarks(
    ayah: SuraAyah,
    added: List<ReadingBookmark>,
    removed: List<ReadingBookmark>
  ): Boolean {
    added.forEach { bookmark ->
      when (bookmark) {
        is AyahReadingBookmark -> setAyahReadingBookmark(bookmark.slot, bookmark.asSuraAyah())
        is PageReadingBookmark -> setPageReadingBookmark(bookmark.slot, bookmark.page)
        is EmptyReadingBookmark -> Unit
      }
    }
    removed.forEach { clearReadingBookmark(it.slot) }
    return true
  }
}
