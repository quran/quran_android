package com.quran.labs.androidquran.feature.reading.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.feature.reading.model.LatestPageTracker
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class RecentPagePresenterTest {

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
  fun `unbind persists the latest visited page`() = runTest {
    val recentPagesDao = FakeRecentPagesDao()
    val presenter = RecentPagePresenter(recentPagesDao, LatestPageTracker(), "madani")

    presenter.bind(flowOf(10, 11, 12))
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.unbind()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(recentPagesDao.recentPages().map { it.page }).containsExactly(12)
  }

  @Test
  fun `unbind replaces the visited page range with the latest page`() = runTest {
    val recentPagesDao = FakeRecentPagesDao()
    recentPagesDao.replaceRecentPages(
      listOf(
        RecentPage(10, 1),
        RecentPage(50, 1),
        RecentPage(12, 1)
      )
    )
    val presenter = RecentPagePresenter(recentPagesDao, LatestPageTracker(), "madani")

    presenter.bind(flowOf(10, 12, 11))
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.unbind()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(recentPagesDao.recentPages().map { it.page }).containsExactly(11, 50).inOrder()
  }

  @Test
  fun `saves are serialized in presenter order`() = runTest {
    val recentPagesDao = RecordingRecentPagesDao()
    val presenter = RecentPagePresenter(recentPagesDao, LatestPageTracker(), "madani")

    presenter.bind(flowOf(10, 11))
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.onJump()
    testDispatcher.scheduler.runCurrent()

    presenter.bind(flowOf(20))
    testDispatcher.scheduler.runCurrent()
    presenter.unbind()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(recentPagesDao.events).containsExactly(
      "start range 10-11->11",
      "finish range 10-11->11",
      "start page 20",
      "finish page 20"
    ).inOrder()
  }

  @Test
  fun `page changes update latest page before persistence`() = runTest {
    val tracker = LatestPageTracker()
    val presenter = RecentPagePresenter(RecordingRecentPagesDao(), tracker, "madani")

    presenter.bind(flowOf(10, 11, 12))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(tracker.latestPage.value?.page).isEqualTo(12)
    assertThat(tracker.latestPage.value?.pageType).isEqualTo("madani")
  }

  private class RecordingRecentPagesDao : RecentPagesDao {
    val events = mutableListOf<String>()

    override fun recentPagesFlow(): Flow<List<RecentPage>> {
      return emptyFlow()
    }

    override suspend fun recentPages(): List<RecentPage> {
      return emptyList()
    }

    override suspend fun addRecentPage(page: Int) {
      events += "start page $page"
      events += "finish page $page"
    }

    override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
      events += "start range $deleteRangeStart-$deleteRangeEnd->$page"
      delay(100)
      events += "finish range $deleteRangeStart-$deleteRangeEnd->$page"
    }

    override suspend fun removeRecentPages() = Unit

    override suspend fun replaceRecentPages(pages: List<RecentPage>) = Unit

    override suspend fun removeRecentsForPage(page: Int) = Unit
  }
}
