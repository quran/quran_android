package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.BookmarksDBAdapter

import io.reactivex.Single
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler

import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

import java.util.concurrent.TimeUnit

class RecentPageModelTest {

  companion object {
    private val SAMPLE_RECENT_PAGES: MutableList<RecentPage> = ArrayList()

    @BeforeClass
    @JvmStatic
    fun setup() {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.io() }

      // sample recent pages
      val timestamp = System.currentTimeMillis()
      SAMPLE_RECENT_PAGES.add(RecentPage(49, timestamp))
      SAMPLE_RECENT_PAGES.add(RecentPage(100, timestamp - 10000))
    }
  }

  @Mock
  private lateinit var bookmarksAdapter: BookmarksDBAdapter

  @Before
  fun setupTest() {
    MockitoAnnotations.openMocks(this@RecentPageModelTest)
  }

  @Test
  fun testEmptyRecentPages() {
    whenever(bookmarksAdapter.getRecentPages()).thenReturn(ArrayList())

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = TestObserver<Int>()
    recentPageModel.latestPageObservable
      .firstOrError()
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
    testObserver.assertValue(Constants.NO_PAGE)
  }

  @Test
  fun testNormalRecentPages() {
    whenever(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES)

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = TestObserver<Int>()
    recentPageModel.latestPageObservable
      .firstOrError()
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
    testObserver.assertValue(49)
  }

  @Test
  fun testUpdateLatestPage() {
    whenever(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES)

    val recentPageModel = RecentPageModel(bookmarksAdapter)

    // make sure the mock data is received and onNext happens
    var testObserver = TestObserver<Int?>()
    recentPageModel.latestPageObservable
      .take(1)
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()

    // after that, let's resubscribe and update with 2 other pages
    testObserver = TestObserver()
    recentPageModel.latestPageObservable
      .take(3)
      .subscribe(testObserver)
    recentPageModel.updateLatestPage(51)
    recentPageModel.updateLatestPage(23)
    testObserver.awaitTerminalEvent()
    testObserver.assertValues(49, 51, 23)
  }

  @Test
  fun testUpdateLatestPageWithSlowRecents() {
    val testScheduler = TestScheduler()

    val recentPageModel: RecentPageModel = object : RecentPageModel(bookmarksAdapter) {
      public override fun getRecentPagesObservable(): Single<List<RecentPage>> {
        // use an implementation of getRecentPagesObservable that delays the results until
        // testScheduler simulates the passing of 5 seconds (the timer time).
        return Single.timer(5, TimeUnit.SECONDS, testScheduler)
          .map<List<RecentPage>> { SAMPLE_RECENT_PAGES }
          .subscribeOn(Schedulers.trampoline())
      }
    }

    val testObserver = TestObserver<Int>()
    recentPageModel.latestPageObservable
      .take(2)
      .subscribe(testObserver)

    // write before the mock data from getRecentPagesObservable comes back
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

    val testObserver = TestObserver<Boolean>()
    recentPageModel.recentPagesUpdatedObservable
      .firstOrError()
      .subscribe(testObserver)

    recentPageModel.persistLatestPage(200, 200, 200)
    testObserver.awaitTerminalEvent()

    verify(bookmarksAdapter, times(1)).addRecentPage(200)
    verify(bookmarksAdapter, times(0)).replaceRecentRangeWithPage(anyInt(), anyInt(), anyInt())
  }

  @Test
  fun testRecentPagesUpdatedWithRange() {
    val recentPageModel = RecentPageModel(bookmarksAdapter)

    val testObserver = TestObserver<Boolean>()
    recentPageModel.recentPagesUpdatedObservable
      .firstOrError()
      .subscribe(testObserver)

    recentPageModel.persistLatestPage(100, 300, 200)
    testObserver.awaitTerminalEvent()

    verify(bookmarksAdapter, times(1)).replaceRecentRangeWithPage(100, 300, 200)
  }
}
