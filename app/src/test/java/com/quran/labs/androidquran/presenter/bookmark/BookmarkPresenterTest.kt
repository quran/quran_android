package com.quran.labs.androidquran.presenter.bookmark

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.awaitTerminalEvent
import com.quran.labs.test.RxSchedulerRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkPresenterTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  private lateinit var quranSettings: QuranSettings
  private lateinit var fakeBookmarksDao: FakeBookmarksDao
  private lateinit var fakeRecentPagesDao: FakeRecentPagesDao

  @Before
  fun setupTest() {
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
    fakeBookmarksDao = FakeBookmarksDao()
    fakeRecentPagesDao = FakeRecentPagesDao()
  }

  @After
  fun teardown() {
    QuranSettings.setInstance(null)
  }

  @Test
  fun `renders ayah bookmarks with inline tag data`() {
    fakeBookmarksDao.setTags(TAGS)
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.tagMap).containsExactly(1L, TAGS[0], 2L, TAGS[1])
    assertThat(result.rows.first()).isInstanceOf(BookmarkRowData.AyahBookmarksHeader::class.java)
    assertThat(result.rows.filterIsInstance<BookmarkRowData.BookmarkItem>()).hasSize(2)
  }

  @Test
  fun `page bookmarks are not rendered`() {
    fakeBookmarksDao.setBookmarks(listOf(PAGE_BOOKMARK) + AYAH_BOOKMARKS.take(1))

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.rows.filterIsInstance<BookmarkRowData.PageBookmarksHeader>()).isEmpty()
    assertThat(result.rows.filterIsInstance<BookmarkRowData.BookmarkItem>())
      .containsExactly(BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS.first(), null))
  }

  @Test
  fun `renders recent pages ahead of ayah bookmarks`() {
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)
    fakeRecentPagesDao.setRecentPages(RECENT_PAGES)

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.rows.take(3)).containsExactly(
      BookmarkRowData.RecentPageHeader(RECENT_PAGES.size),
      BookmarkRowData.RecentPage(RECENT_PAGES[0]),
      BookmarkRowData.RecentPage(RECENT_PAGES[1])
    ).inOrder()
  }

  @Test
  fun `renders grouped tag rows`() {
    fakeBookmarksDao.setTags(TAGS)
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)

    val result = getBookmarkResultAndValidate(makeBookmarkPresenter(), BookmarkSortOrder.SORT_DATE_ADDED, true)

    assertThat(result.rows).containsAtLeast(
      BookmarkRowData.TagHeader(TAGS[0]),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], 1),
      BookmarkRowData.TagHeader(TAGS[1]),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], 2),
      BookmarkRowData.NotTaggedHeader,
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[1], null),
    ).inOrder()
  }

  @Test
  fun `group by tags toggles setting`() {
    quranSettings.bookmarksGroupedByTags = true
    val presenter = makeBookmarkPresenter()

    presenter.toggleGroupByTags()

    assertThat(presenter.isGroupedByTags).isFalse()
    assertThat(presenter.shouldShowInlineTags()).isTrue()
    assertThat(quranSettings.bookmarksGroupedByTags).isFalse()
  }

  @Test
  fun `contextual actions allow editing one tag header and tagging bookmark rows`() {
    val presenter = makeBookmarkPresenter()
    val tagHeaderResult = presenter.getContextualOperationsForItems(
      listOf(QuranRow.Builder().withType(QuranRow.BOOKMARK_HEADER).withTagId(1).build())
    )
    val bookmarkResult = presenter.getContextualOperationsForItems(
      listOf(
        QuranRow.Builder().withType(QuranRow.AYAH_BOOKMARK).build()
      )
    )

    assertThat(tagHeaderResult.asList()).containsExactly(true, true, false).inOrder()
    assertThat(bookmarkResult.asList()).containsExactly(false, true, true).inOrder()
  }

  @Test
  fun `location sort delegates to bookmarks dao`() {
    fakeBookmarksDao.setBookmarks(
      listOf(
        Bookmark(1, 4, 1, 75, 2),
        Bookmark(2, 2, 255, 42, 1)
      )
    )

    val result = getBookmarkResultAndValidate(makeBookmarkPresenter(), BookmarkSortOrder.SORT_LOCATION)

    assertThat(result.rows.filterIsInstance<BookmarkRowData.BookmarkItem>().map { it.bookmark.id })
      .containsExactly(2L, 1L)
      .inOrder()
  }

  private fun makeBookmarkPresenter(): BookmarkPresenter {
    return object : BookmarkPresenter(
      fakeBookmarksDao,
      fakeRecentPagesDao,
      quranSettings,
      { throw IllegalStateException("ArabicDatabaseUtils not wired up in test") },
    ) {
      override fun subscribeToChanges() {
        // nothing
      }
    }
  }

  private fun getBookmarkResultByDateAndValidate(presenter: BookmarkPresenter): BookmarkRawResult {
    return getBookmarkResultAndValidate(presenter, BookmarkSortOrder.SORT_DATE_ADDED)
  }

  private fun getBookmarkResultAndValidate(
    presenter: BookmarkPresenter,
    sortOrder: Int,
    groupByTags: Boolean = false
  ): BookmarkRawResult {
    val testObserver = presenter
      .getBookmarksListObservable(sortOrder, groupByTags)
      .test()
    testObserver.awaitTerminalEvent()
    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    return testObserver.values()[0]
  }

  private companion object {
    private val TAGS = listOf(
      Tag(1, "Review"),
      Tag(2, "Important")
    )
    private val AYAH_BOOKMARKS = listOf(
      Bookmark(42, 46, 1, 502, 200, listOf(1, 2)),
      Bookmark(2, 2, 4, 2, 100)
    )
    private val PAGE_BOOKMARK = Bookmark(23, null, null, 400, 300)
    private val RECENT_PAGES = listOf(
      RecentPage(42, 200),
      RecentPage(43, 100)
    )
  }
}
