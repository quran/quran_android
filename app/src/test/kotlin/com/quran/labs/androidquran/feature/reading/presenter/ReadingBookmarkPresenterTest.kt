package com.quran.labs.androidquran.feature.reading.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import kotlinx.coroutines.Dispatchers
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
    val readingBookmarksDao = FakeReadingBookmarksDao(AyahReadingBookmark(2, 255, timestamp = 1))
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
}
