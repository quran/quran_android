package com.quran.labs.androidquran.presenter.bookmark

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkPresenterTest {

  private lateinit var quranSettings: QuranSettings
  private lateinit var fakeBookmarksDao: FakeBookmarksDao
  private lateinit var fakeRecentPagesDao: FakeRecentPagesDao
  private lateinit var fakeReadingBookmarksDao: FakeReadingBookmarksDao
  private lateinit var fakeHighlightsDao: FakeHighlightsDao

  @Before
  fun setupTest() {
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
    fakeBookmarksDao = FakeBookmarksDao()
    fakeRecentPagesDao = FakeRecentPagesDao()
    fakeReadingBookmarksDao = FakeReadingBookmarksDao()
    fakeHighlightsDao = FakeHighlightsDao()
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

    assertThat(result.tagMap).containsExactly("tag-1", TAGS[0], "tag-2", TAGS[1])
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
  fun `renders reading bookmark ahead of recent pages`() {
    val readingBookmark = PageReadingBookmark(42, 300)
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)
    fakeRecentPagesDao.setRecentPages(RECENT_PAGES)
    fakeReadingBookmarksDao.setReadingBookmark(readingBookmark)

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.rows.take(5)).containsExactly(
      BookmarkRowData.ReadingBookmarkHeader,
      BookmarkRowData.ReadingBookmarkItem(readingBookmark),
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
      BookmarkRowData.TagHeader(TAGS[0], 1, false),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], "tag-1"),
      BookmarkRowData.TagHeader(TAGS[1], 1, false),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], "tag-2"),
      BookmarkRowData.NotTaggedHeader(1, false),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[1], null),
    ).inOrder()
  }

  @Test
  fun `collapsed collection hides its bookmarks but keeps its header and count`() {
    fakeBookmarksDao.setTags(TAGS)
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)
    val presenter = makeBookmarkPresenter()

    presenter.toggleCollectionCollapsed("tag-1")
    val result = getBookmarkResultAndValidate(
      presenter, BookmarkSortOrder.SORT_DATE_ADDED, true
    )

    assertThat(result.rows).containsAtLeast(
      BookmarkRowData.TagHeader(TAGS[0], 1, true),
      BookmarkRowData.TagHeader(TAGS[1], 1, false),
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], "tag-2"),
    ).inOrder()
    assertThat(result.rows).doesNotContain(
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], "tag-1")
    )
    assertThat(quranSettings.collapsedCollections).containsExactly("tag-1")
  }

  @Test
  fun `deleting a bookmark drops the collection count while the undo window is open`() {
    fakeBookmarksDao.setTags(TAGS)
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)
    quranSettings.bookmarksGroupedByTags = true
    val presenter = makeBookmarkPresenter()
    val current = getBookmarkResultAndValidate(
      presenter, BookmarkSortOrder.SORT_DATE_ADDED, true
    )

    val preview = presenter.previewAfterDeletion(
      current,
      listOf(
        QuranRow.Builder()
          .withType(QuranRow.AYAH_BOOKMARK)
          .withBookmark(AYAH_BOOKMARKS[0])
          .withTagId("tag-1")
          .build()
      )
    )

    assertThat(preview.rows).contains(BookmarkRowData.TagHeader(TAGS[0], 0, false))
    assertThat(preview.rows).contains(BookmarkRowData.TagHeader(TAGS[1], 1, false))
    assertThat(preview.rows).doesNotContain(
      BookmarkRowData.BookmarkItem(AYAH_BOOKMARKS[0], "tag-1")
    )
  }

  @Test
  fun `renders highlights after recent pages, with every color and its count`() {
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)
    fakeRecentPagesDao.setRecentPages(RECENT_PAGES)
    fakeHighlightsDao.setHighlights(HIGHLIGHTS)

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.rows.take(9)).containsExactly(
      BookmarkRowData.RecentPageHeader(RECENT_PAGES.size),
      BookmarkRowData.RecentPage(RECENT_PAGES[0]),
      BookmarkRowData.RecentPage(RECENT_PAGES[1]),
      BookmarkRowData.HighlightsHeader,
      BookmarkRowData.HighlightColorItem(HighlightColor.YELLOW, 0),
      BookmarkRowData.HighlightColorItem(HighlightColor.GREEN, 1),
      BookmarkRowData.HighlightColorItem(HighlightColor.BLUE, 2),
      BookmarkRowData.HighlightColorItem(HighlightColor.RED, 0),
      BookmarkRowData.HighlightColorItem(HighlightColor.PURPLE, 0)
    ).inOrder()
  }

  @Test
  fun `highlights section is hidden when there are no highlights`() {
    fakeBookmarksDao.setBookmarks(AYAH_BOOKMARKS)

    val result = getBookmarkResultByDateAndValidate(makeBookmarkPresenter())

    assertThat(result.rows).doesNotContain(BookmarkRowData.HighlightsHeader)
    assertThat(result.rows.filterIsInstance<BookmarkRowData.HighlightColorItem>()).isEmpty()
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
      listOf(QuranRow.Builder().withType(QuranRow.BOOKMARK_HEADER).withTagId("tag-1").build())
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
  fun `contextual actions ignore non-tag collection header`() {
    val presenter = makeBookmarkPresenter()
    val result = presenter.getContextualOperationsForItems(
      listOf(
        QuranRow.Builder()
          .withType(QuranRow.BOOKMARK_HEADER)
          .build()
      )
    )

    assertThat(result.asList()).containsExactly(false, false, false).inOrder()
  }

  @Test
  fun `location sort delegates to bookmarks dao`() {
    fakeBookmarksDao.setBookmarks(
      listOf(
        Bookmark("bookmark-1", 4, 1, 75, 2),
        Bookmark("bookmark-2", 2, 255, 42, 1)
      )
    )

    val result = getBookmarkResultAndValidate(makeBookmarkPresenter(), BookmarkSortOrder.SORT_LOCATION)

    assertThat(result.rows.filterIsInstance<BookmarkRowData.BookmarkItem>().map { it.bookmark.id })
      .containsExactly("bookmark-2", "bookmark-1")
      .inOrder()
  }

  private fun makeBookmarkPresenter(): BookmarkPresenter {
    return object : BookmarkPresenter(
      fakeBookmarksDao,
      fakeRecentPagesDao,
      fakeReadingBookmarksDao,
      fakeHighlightsDao,
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
    return runBlocking {
      presenter.getBookmarksList(sortOrder, groupByTags)
    }
  }

  private companion object {
    private val TAGS = listOf(
      Tag("tag-1", "Review"),
      Tag("tag-2", "Important")
    )
    private val AYAH_BOOKMARKS = listOf(
      Bookmark("bookmark-42", 46, 1, 502, 200, listOf("tag-1", "tag-2")),
      Bookmark("bookmark-2", 2, 4, 2, 100)
    )
    private val PAGE_BOOKMARK = Bookmark("bookmark-23", null, null, 400, 300)
    private val RECENT_PAGES = listOf(
      RecentPage(42, 200),
      RecentPage(43, 100)
    )
    private val HIGHLIGHTS = listOf(
      Highlight(SuraAyah(2, 255), HighlightColor.BLUE, Instant.fromEpochSeconds(300)),
      Highlight(SuraAyah(3, 93), HighlightColor.BLUE, Instant.fromEpochSeconds(200)),
      Highlight(SuraAyah(50, 44), HighlightColor.GREEN, Instant.fromEpochSeconds(100))
    )
  }
}
