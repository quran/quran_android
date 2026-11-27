package com.quran.labs.androidquran.presenter.bookmark

import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkPresenterLeakTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var quranSettings: QuranSettings
  private lateinit var fakeBookmarksDao: FakeBookmarksDao
  private lateinit var fakeRecentPagesDao: FakeRecentPagesDao
  private lateinit var fakeReadingBookmarksDao: FakeReadingBookmarksDao
  private lateinit var fakeHighlightsDao: FakeHighlightsDao

  private var activeChangeCollectors = 0
  private var activeRecentPagesCollectors = 0
  private var activeReadingBookmarkCollectors = 0
  private var activeHighlightCollectors = 0

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
    quranSettings.pageType = "madani"
    fakeBookmarksDao = FakeBookmarksDao()
    fakeRecentPagesDao = FakeRecentPagesDao()
    fakeReadingBookmarksDao = FakeReadingBookmarksDao()
    fakeHighlightsDao = FakeHighlightsDao()
    activeChangeCollectors = 0
    activeRecentPagesCollectors = 0
    activeReadingBookmarkCollectors = 0
    activeHighlightCollectors = 0
  }

  @After
  fun teardown() {
    QuranSettings.setInstance(null)
    Dispatchers.resetMain()
  }

  @Test
  fun `does not collect flows before bind and cancels all collectors on unbind`() = runTest {
    val presenter = makePresenter(testDispatcher)
    val fragment = createAttachedFragment()

    // Before bind: zero active flow collectors
    assertThat(totalActiveCollectors()).isEqualTo(0)

    // After bind: all 4 change flows are collected
    presenter.bind(fragment)
    assertThat(activeChangeCollectors).isEqualTo(1)
    assertThat(activeRecentPagesCollectors).isEqualTo(1)
    assertThat(activeReadingBookmarkCollectors).isEqualTo(1)
    assertThat(activeHighlightCollectors).isEqualTo(1)

    // After unbind: all 4 collectors are cancelled
    presenter.unbind(fragment)
    assertThat(totalActiveCollectors()).isEqualTo(0)

    // Re-bind and unbind again works cleanly without leaking collectors
    presenter.bind(fragment)
    assertThat(totalActiveCollectors()).isEqualTo(4)
    presenter.unbind(fragment)
    assertThat(totalActiveCollectors()).isEqualTo(0)
  }

  @Test
  fun `unbind before in-flight load finishes cancels load without caching stale data`() = runTest {
    val standardDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(standardDispatcher)
    var tagReads = 0
    val countingTagsDao = object : BookmarksDao by fakeBookmarksDao {
      override suspend fun tags(): List<Tag> {
        tagReads++
        return fakeBookmarksDao.tags()
      }
    }
    val presenter = BookmarkPresenter.forTest(
      countingTagsDao,
      fakeRecentPagesDao,
      fakeReadingBookmarksDao,
      fakeHighlightsDao,
      quranSettings,
      { throw IllegalStateException("ArabicDatabaseUtils not needed in leak test") },
      standardDispatcher
    )
    val fragment = createAttachedFragment()

    presenter.bind(fragment)
    // Unbind before standardDispatcher executes the pending load
    presenter.unbind(fragment)
    testScheduler.advanceUntilIdle()
    assertThat(tagReads).isEqualTo(0)

    // Rebinding performs a fresh load rather than serving stale cachedData
    presenter.bind(fragment)
    testScheduler.advanceUntilIdle()
    assertThat(tagReads).isEqualTo(1)
    presenter.unbind(fragment)
  }

  @Test
  fun `groups bookmarks across multiple tags in single pass`() = runTest {
    val tags = listOf(
      Tag("tag-1", "Review"),
      Tag("tag-2", "Important"),
      Tag("tag-3", "Empty")
    )
    val bookmarks = listOf(
      Bookmark("b-1", 2, 255, 42, 100, listOf("tag-1", "tag-2")),
      Bookmark("b-2", 3, 10, 51, 200, listOf("tag-2")),
      Bookmark("b-page", null, null, 10, 300, listOf("tag-1"))
    )
    fakeBookmarksDao.setTags(tags)
    fakeBookmarksDao.setBookmarks(bookmarks)
    val presenter = makePresenter()

    val result = presenter.getBookmarksList(BookmarkSortOrder.SORT_DATE_ADDED, groupByTags = true)

    val headers = result.rows.filterIsInstance<BookmarkRowData.TagHeader>()
    assertThat(headers.map { it.tag.id to it.count }).containsExactly(
      "fake-default" to 2,
      "tag-1" to 1,
      "tag-2" to 2,
      "tag-3" to 0
    ).inOrder()
  }

  private fun totalActiveCollectors(): Int {
    return activeChangeCollectors +
      activeRecentPagesCollectors +
      activeReadingBookmarkCollectors +
      activeHighlightCollectors
  }

  private fun createAttachedFragment(): BookmarksFragment {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
    val fragment = BookmarksFragment()
    activity.supportFragmentManager
      .beginTransaction()
      .add(fragment, "bookmarks")
      .commitNow()
    return fragment
  }

  private fun makePresenter(
    ioDispatcher: CoroutineDispatcher = testDispatcher
  ): BookmarkPresenter {
    val countingBookmarksDao = object : BookmarksDao by fakeBookmarksDao {
      override val changes: Flow<Unit> = fakeBookmarksDao.changes
        .onStart { activeChangeCollectors++ }
        .onCompletion { activeChangeCollectors-- }
    }
    val countingRecentPagesDao = object : RecentPagesDao by fakeRecentPagesDao {
      override fun recentPagesFlow() = fakeRecentPagesDao.recentPagesFlow()
        .onStart { activeRecentPagesCollectors++ }
        .onCompletion { activeRecentPagesCollectors-- }
    }
    val countingReadingBookmarksDao = object : ReadingBookmarksDao by fakeReadingBookmarksDao {
      override fun readingBookmarksFlow() = fakeReadingBookmarksDao.readingBookmarksFlow()
        .onStart { activeReadingBookmarkCollectors++ }
        .onCompletion { activeReadingBookmarkCollectors-- }
    }
    val countingHighlightsDao = object : HighlightsDao by fakeHighlightsDao {
      override fun highlightsFlow() = fakeHighlightsDao.highlightsFlow()
        .onStart { activeHighlightCollectors++ }
        .onCompletion { activeHighlightCollectors-- }
    }
    return BookmarkPresenter.forTest(
      countingBookmarksDao,
      countingRecentPagesDao,
      countingReadingBookmarksDao,
      countingHighlightsDao,
      quranSettings,
      {
        throw IllegalStateException("ArabicDatabaseUtils not needed in leak test")
      },
      ioDispatcher
    )
  }
}
