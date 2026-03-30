package com.quran.labs.androidquran.presenter.bookmark

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.fakes.FakeBookmarkModel
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
import androidx.test.core.app.ApplicationProvider

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkPresenterTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  companion object {
    private val TAG_LIST: MutableList<Tag> = ArrayList(2)
    private val RECENT_LIST: MutableList<RecentPage> = ArrayList(1)
    private val AYAH_BOOKMARKS_LIST: MutableList<Bookmark> = ArrayList(2)
    private val MIXED_BOOKMARKS_LIST: MutableList<Bookmark>
    private val AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG: Int
    private val MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG: Int

    init {
      // a list of two tags
      TAG_LIST.add(Tag(1, "First Tag"))
      TAG_LIST.add(Tag(2, "Second Tag"))

      // recent page
      RECENT_LIST.add(RecentPage(42, System.currentTimeMillis()))

      // two ayah bookmarks
      AYAH_BOOKMARKS_LIST.add(
        Bookmark(42, 46, 1, 502, System.currentTimeMillis(), listOf(2L))
      )
      AYAH_BOOKMARKS_LIST.add(Bookmark(2, 2, 4, 2, System.currentTimeMillis() - 60000))

      // two ayah bookmarks and one page bookmark
      MIXED_BOOKMARKS_LIST = ArrayList(AYAH_BOOKMARKS_LIST)
      MIXED_BOOKMARKS_LIST.add(
        0,
        Bookmark(23, null, null, 400, System.currentTimeMillis() + 1, listOf(1L, 2L))
      )

      // figure out how many rows the bookmarks would occupy if grouped by tags - this is really
      // the max between number of tags and 1 for each bookmark.
      var total = 0
      for (bookmark in AYAH_BOOKMARKS_LIST) {
        val tags = bookmark.tags.size
        total += tags.coerceAtLeast(1)
      }
      AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG = total

      total = 0
      for (bookmark in MIXED_BOOKMARKS_LIST) {
        val tags = bookmark.tags.size
        total += tags.coerceAtLeast(1)
      }
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG = total
    }
  }

  private lateinit var quranSettings: QuranSettings
  private lateinit var fakeModel: FakeBookmarkModel

  @Before
  fun setupTest() {
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
    fakeModel = FakeBookmarkModel()
  }

  @After
  fun teardown() {
    QuranSettings.setInstance(null)
  }

  @Test
  fun testBookmarkObservableAyahBookmarksByDate() {
    // Arrange
    fakeModel.setTags(emptyList())
    fakeModel.setBookmarks(AYAH_BOOKMARKS_LIST)
    fakeModel.setRecentPages(emptyList())

    // Act
    val presenter = makeBookmarkPresenter()
    val result = getBookmarkResultByDateAndValidate(presenter, false)

    // Assert
    assertThat(result.tagMap).isEmpty()
    // 1 for the header, plus one row per item
    assertThat(result.rows).hasSize(AYAH_BOOKMARKS_LIST.size + 1)
  }

  @Test
  fun testBookmarkObservableMixedBookmarksByDate() {
    // Arrange
    fakeModel.setTags(emptyList())
    fakeModel.setBookmarks(MIXED_BOOKMARKS_LIST)
    fakeModel.setRecentPages(emptyList())

    // Act
    val presenter = makeBookmarkPresenter()
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, false)

    // Assert
    assertThat(tagMap).isEmpty()
    // 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(rows).hasSize(MIXED_BOOKMARKS_LIST.size + 2)
  }

  @Test
  fun testBookmarkObservableMixedBookmarksByDateWithRecentPage() {
    // Arrange
    fakeModel.setTags(TAG_LIST)
    fakeModel.setBookmarks(MIXED_BOOKMARKS_LIST)
    fakeModel.setRecentPages(RECENT_LIST)

    // Act
    val presenter = makeBookmarkPresenter()
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, false)

    // Assert
    assertThat(tagMap).hasSize(2)
    // 2 for "current page", 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(rows).hasSize(MIXED_BOOKMARKS_LIST.size + 4)
  }

  @Test
  fun testBookmarkObservableAyahBookmarksGroupedByTag() {
    // Arrange
    fakeModel.setTags(TAG_LIST)
    fakeModel.setBookmarks(AYAH_BOOKMARKS_LIST)
    fakeModel.setRecentPages(emptyList())

    // Act
    val presenter = makeBookmarkPresenter()
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)

    // Assert
    assertThat(tagMap).hasSize(2)
    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(rows).hasSize(
      AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1
    )
  }

  @Test
  fun testBookmarkObservableMixedBookmarksGroupedByTag() {
    // Arrange
    fakeModel.setTags(TAG_LIST)
    fakeModel.setBookmarks(MIXED_BOOKMARKS_LIST)
    fakeModel.setRecentPages(emptyList())

    // Act
    val presenter = makeBookmarkPresenter()
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)

    // Assert
    assertThat(tagMap).hasSize(2)
    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(rows).hasSize(
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1
    )
  }

  @Test
  fun testBookmarkObservableMixedBookmarksGroupedByTagWithRecentPage() {
    // Arrange
    fakeModel.setTags(TAG_LIST)
    fakeModel.setBookmarks(MIXED_BOOKMARKS_LIST)
    fakeModel.setRecentPages(RECENT_LIST)

    // Act
    val presenter = makeBookmarkPresenter()
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)

    // Assert
    assertThat(tagMap).hasSize(2)
    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted, plus
    // current page header, plus current page
    assertThat(rows).hasSize(
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1 + 2
    )
  }

  @Test
  fun `returns empty rows when no bookmarks or tags exist`() {
    // Arrange - fakeModel has no data by default
    val presenter = makeBookmarkPresenter()

    // Act
    val result = getBookmarkResultByDateAndValidate(presenter, false)

    // Assert
    assertThat(result.rows).isEmpty()
    assertThat(result.tagMap).isEmpty()
  }

  @Test
  fun `renders page bookmark section when all bookmarks are page bookmarks`() {
    // Arrange
    fakeModel.setBookmarks(
      listOf(
        Bookmark(1, null, null, 2, System.currentTimeMillis()),
        Bookmark(2, null, null, 5, System.currentTimeMillis() - 1000),
      )
    )
    val presenter = makeBookmarkPresenter()

    // Act
    val result = getBookmarkResultByDateAndValidate(presenter, false)

    // Assert
    assertThat(result.rows.first()).isInstanceOf(BookmarkRowData.PageBookmarksHeader::class.java)
    assertThat(result.rows.filterIsInstance<BookmarkRowData.AyahBookmarksHeader>()).isEmpty()
    assertThat(result.rows).hasSize(3) // 1 header + 2 bookmarks
  }

  @Test
  fun `grouped-by-tags row count is independent of sort order`() {
    // Arrange
    fakeModel.setTags(TAG_LIST)
    fakeModel.setBookmarks(MIXED_BOOKMARKS_LIST)
    val presenter = makeBookmarkPresenter()

    // Act - use SORT_LOCATION instead of SORT_DATE_ADDED
    val testObserver = presenter
      .getBookmarksListObservable(BookmarksDBAdapter.SORT_LOCATION, true)
      .test()
    testObserver.awaitTerminalEvent()
    testObserver.assertNoErrors()
    val result = testObserver.values()[0]

    // Assert - tag grouping is order-independent so row count matches date-sorted variant
    assertThat(result.rows).hasSize(
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1
    )
  }

  @Test
  fun `toggleGroupByTags flips isGroupedByTags and persists to settings`() {
    // Arrange
    val presenter = makeBookmarkPresenter()
    assertThat(presenter.isGroupedByTags).isFalse()

    // Act
    presenter.toggleGroupByTags()

    // Assert
    assertThat(presenter.isGroupedByTags).isTrue()
    assertThat(quranSettings.bookmarksGroupedByTags).isTrue()
  }

  @Test
  fun `shouldShowInlineTags returns true when not grouped by tags`() {
    // Arrange
    val presenter = makeBookmarkPresenter()

    // Act + Assert
    assertThat(presenter.shouldShowInlineTags()).isTrue()

    presenter.toggleGroupByTags()
    assertThat(presenter.shouldShowInlineTags()).isFalse()
  }

  @Test
  fun `getContextualOperationsForItems returns rename-tag and has-items flags for tag-header-only selection`() {
    // Arrange
    val presenter = makeBookmarkPresenter()
    val rows = listOf(
      QuranRow.Builder().withType(QuranRow.BOOKMARK_HEADER).withTagId(1).build()
    )

    // Act
    val result = presenter.getContextualOperationsForItems(rows)

    // Assert [headers=1, bookmarks=0]
    assertThat(result[0]).isTrue()   // headers==1 && bookmarks==0
    assertThat(result[1]).isTrue()   // (1+0)>0
    assertThat(result[2]).isFalse()  // headers!=0
  }

  @Test
  fun `getContextualOperationsForItems returns delete and has-items flags for bookmark-only selection`() {
    // Arrange
    val presenter = makeBookmarkPresenter()
    val rows = listOf(
      QuranRow.Builder().withType(QuranRow.PAGE_BOOKMARK).build(),
      QuranRow.Builder().withType(QuranRow.AYAH_BOOKMARK).build(),
    )

    // Act
    val result = presenter.getContextualOperationsForItems(rows)

    // Assert [headers=0, bookmarks=2]
    assertThat(result[0]).isFalse()  // headers!=1
    assertThat(result[1]).isTrue()   // (0+2)>0
    assertThat(result[2]).isTrue()   // headers==0 && bookmarks>0
  }

  @Test
  fun `getContextualOperationsForItems returns has-items flag only for mixed selection`() {
    // Arrange
    val presenter = makeBookmarkPresenter()
    val rows = listOf(
      QuranRow.Builder().withType(QuranRow.BOOKMARK_HEADER).withTagId(1).build(),
      QuranRow.Builder().withType(QuranRow.PAGE_BOOKMARK).build(),
    )

    // Act
    val result = presenter.getContextualOperationsForItems(rows)

    // Assert [headers=1, bookmarks=1]
    assertThat(result[0]).isFalse()  // bookmarks!=0
    assertThat(result[1]).isTrue()   // (1+1)>0
    assertThat(result[2]).isFalse()  // headers!=0
  }

  private fun makeBookmarkPresenter(): BookmarkPresenter {
    return object : BookmarkPresenter(
      fakeModel,
      quranSettings,
      { throw IllegalStateException("ArabicDatabaseUtils not wired up in test") },
    ) {
      override fun subscribeToChanges() {
        // nothing
      }
    }
  }

  private fun getBookmarkResultByDateAndValidate(
    presenter: BookmarkPresenter,
    groupByTags: Boolean,
  ): BookmarkRawResult {
    val testObserver = presenter
      .getBookmarksListObservable(BookmarksDBAdapter.SORT_DATE_ADDED, groupByTags)
      .test()
    testObserver.awaitTerminalEvent()
    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    return testObserver.values()[0]
  }
}
