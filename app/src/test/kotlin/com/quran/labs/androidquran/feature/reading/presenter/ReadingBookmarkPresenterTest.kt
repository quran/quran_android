package com.quran.labs.androidquran.feature.reading.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class ReadingBookmarkPresenterTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `page icon is selected for exact page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(42, 1))
    val screen = RecordingScreen()
    val presenter = ReadingBookmarkPresenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(true)
  }

  @Test
  fun `page icon is not selected for a different page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(43, 1))
    val screen = RecordingScreen()
    val presenter = ReadingBookmarkPresenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `page icon is not selected for ayah reading bookmark on current page`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(AyahReadingBookmark(2, 255, 42, 1))
    val screen = RecordingScreen()
    val presenter = ReadingBookmarkPresenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `toggle delegates current page to dao and updates screen`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = ReadingBookmarkPresenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(readingBookmarksDao.toggledPages).containsExactly(42)
    assertThat(screen.pageBookmarkStates.last()).isTrue()
  }

  private class RecordingScreen : ReadingBookmarkPresenter.Screen {
    val pageBookmarkStates = mutableListOf<Boolean>()

    override fun setPageReadingBookmarkSelected(isBookmarked: Boolean) {
      pageBookmarkStates += isBookmarked
    }
  }

  private class FakeReadingBookmarksDao(
    initialBookmark: ReadingBookmark? = null
  ) : ReadingBookmarksDao {
    private val readingBookmark = MutableStateFlow(initialBookmark)
    val toggledPages = mutableListOf<Int>()

    override fun readingBookmarkFlow(): Flow<ReadingBookmark?> {
      return readingBookmark
    }

    override suspend fun readingBookmark(): ReadingBookmark? {
      return readingBookmark.value
    }

    override suspend fun setPageReadingBookmark(page: Int): Boolean {
      readingBookmark.value = PageReadingBookmark(page, 1)
      return true
    }

    override suspend fun setAyahReadingBookmark(suraAyah: SuraAyah): Boolean {
      readingBookmark.value = AyahReadingBookmark(suraAyah.sura, suraAyah.ayah, page = 1, timestamp = 1)
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
        readingBookmark.value = PageReadingBookmark(page, 1)
        true
      }
    }
  }
}
