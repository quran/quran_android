package com.quran.labs.androidquran.model.bookmark

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.awaitTerminalEvent
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import com.quran.labs.test.RxSchedulerRule
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for RecentPageModel using in-memory SQLite database.
 * Migrated from Mockito to real database implementation.
 */
class RecentPageModelTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  companion object {
    private val SAMPLE_RECENT_PAGES = mutableListOf<RecentPage>()

    init {
      val timestamp = System.currentTimeMillis()
      SAMPLE_RECENT_PAGES.add(RecentPage(49, timestamp))
      SAMPLE_RECENT_PAGES.add(RecentPage(100, timestamp - 10000))
    }
  }

  private lateinit var database: BookmarksDatabase
  private lateinit var bookmarksAdapter: BookmarksDBAdapter

  @Before
  fun setupTest() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BookmarksDatabase.Schema.create(driver)
    database = BookmarksDatabase(
      driver,
      Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
      Last_pages.Adapter(IntColumnAdapter)
    )
    bookmarksAdapter = BookmarksDBAdapter(database)
  }

  @Test
  fun testEmptyRecentPages() {
    // No pages in database initially
    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = recentPageModel.getLatestPageObservable()
      .firstOrError()
      .test()
    testObserver.awaitTerminalEvent()
    testObserver.assertValue(Constants.NO_PAGE)
  }

  @Test
  fun testNormalRecentPages() {
    // Setup: Add sample pages to database
    SAMPLE_RECENT_PAGES.forEach { page ->
      bookmarksAdapter.addRecentPage(page.page)
    }

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = recentPageModel.getLatestPageObservable()
      .firstOrError()
      .test()
    testObserver.awaitTerminalEvent()

    // First page added is the most recent
    testObserver.assertValue(49)
  }

  @Test
  fun testUpdateLatestPage() {
    // Setup: Add sample pages to database
    SAMPLE_RECENT_PAGES.forEach { page ->
      bookmarksAdapter.addRecentPage(page.page)
    }

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    // make sure the data is received and onNext happens
    val testObserver1 = recentPageModel.getLatestPageObservable()
      .take(1)
      .test()
    testObserver1.awaitTerminalEvent()

    // after that, let's resubscribe and update with 2 other pages
    val testObserver = recentPageModel.getLatestPageObservable()
      .take(3)
      .test()
    recentPageModel.updateLatestPage(51)
    recentPageModel.updateLatestPage(23)

    testObserver.awaitTerminalEvent()
    testObserver.assertValues(49, 51, 23)
  }

  @Test
  fun testUpdateLatestPageWithSlowRecents() {
    val testScheduler = TestScheduler()

    val recentPageModel = object : RecentPageModel(bookmarksAdapter) {
      override fun getRecentPagesObservable(): Single<List<RecentPage>> {
        // use an implementation of getRecentPagesObservable that delays the results until
        // testScheduler simulates the passing of 5 seconds (the timer time).
        return Single.timer(5, TimeUnit.SECONDS, testScheduler)
          .map { SAMPLE_RECENT_PAGES.toList() }
          .subscribeOn(Schedulers.trampoline())
      }
    }

    val testObserver = recentPageModel.getLatestPageObservable()
      .take(2)
      .test()

    // write before the data from getRecentPagesObservable comes back
    recentPageModel.updateLatestPage(51)

    // now let's pretend the recent page came back - we expect that this page should be ignored.
    testScheduler.advanceTimeBy(5, TimeUnit.SECONDS)

    // and that another page afterwards was written
    recentPageModel.updateLatestPage(23)

    testObserver.awaitTerminalEvent()
    testObserver.assertValues(51, 23)
  }

  @Test
  fun testRecentPagesUpdated() {
    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = recentPageModel.getRecentPagesUpdatedObservable()
      .firstOrError()
      .test()

    recentPageModel.persistLatestPage(200, 200, 200)
    testObserver.awaitTerminalEvent()

    // Verify: Recent page was added to database
    val recentPages = bookmarksAdapter.getRecentPages()
    assertThat(recentPages).hasSize(1)
    assertThat(recentPages[0].page).isEqualTo(200)
  }

  @Test
  fun testRecentPagesUpdatedWithRange() {
    // Setup: Add pages in the range that will be replaced
    bookmarksAdapter.addRecentPage(150)
    bookmarksAdapter.addRecentPage(250)

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = recentPageModel.getRecentPagesUpdatedObservable()
      .firstOrError()
      .test()

    recentPageModel.persistLatestPage(100, 300, 200)
    testObserver.awaitTerminalEvent()

    // Verify: Pages in range [100, 300] were replaced with page 200
    val recentPages = bookmarksAdapter.getRecentPages()
    // Should have page 200 but not 150 or 250 (they're in the range [100, 300])
    val pageNumbers = recentPages.map { it.page }
    assertThat(pageNumbers).contains(200)
    assertThat(pageNumbers).doesNotContain(150)
    assertThat(pageNumbers).doesNotContain(250)
  }
}
