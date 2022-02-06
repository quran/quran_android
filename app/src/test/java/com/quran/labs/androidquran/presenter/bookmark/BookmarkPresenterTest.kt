package com.quran.labs.androidquran.presenter.bookmark

import android.content.Context
import android.content.res.Resources

import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.data.pageinfo.common.MadaniDataSource

import com.quran.labs.androidquran.dao.bookmark.BookmarkResult
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import com.quran.labs.androidquran.ui.helpers.QuranRowFactory
import com.quran.labs.androidquran.util.QuranSettings

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.schedulers.Schedulers

import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import com.google.common.truth.Truth.assertThat
import com.quran.labs.awaitTerminalEvent
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

class BookmarkPresenterTest {

  companion object {
    private val TAG_LIST: MutableList<Tag> = ArrayList(2)
    private val RECENT_LIST: MutableList<RecentPage> = ArrayList(1)
    private val AYAH_BOOKMARKS_LIST: MutableList<Bookmark> = ArrayList(2)
    private val MIXED_BOOKMARKS_LIST: MutableList<Bookmark>
    private val RESOURCE_ARRAY: Array<String>
    private val AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG: Int
    private val MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG: Int

    @BeforeClass
    @JvmStatic
    fun setup() {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.io() }
    }

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

      // we return this fake array when getStringArray is called
      RESOURCE_ARRAY = Array(114) { it.toString() }

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

  @Mock
  private lateinit var appContext: Context

  @Mock
  private lateinit var resources: Resources

  @Mock
  private lateinit var settings: QuranSettings

  @Mock
  private lateinit var bookmarksAdapter: BookmarksDBAdapter

  @Mock
  private lateinit var recentPageModel: RecentPageModel

  @Before
  fun setupTest() {
    MockitoAnnotations.openMocks(this@BookmarkPresenterTest)

    QuranSettings.setInstance(settings)
    whenever(appContext.getString(anyInt())).thenReturn("Test")
    whenever(appContext.resources).thenReturn(resources)
    whenever(resources.getStringArray(anyInt())).thenReturn(RESOURCE_ARRAY)
    whenever(appContext.applicationContext).thenReturn(appContext)
  }

  @Test
  fun testBookmarkObservableAyahBookmarksByDate() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(ArrayList()),
          Single.just(AYAH_BOOKMARKS_LIST),
          Single.just(ArrayList())
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    val presenter = makeBookmarkPresenter(model)
    val result = getBookmarkResultByDateAndValidate(presenter, false)
    assertThat(result.tagMap).isEmpty()
    // 1 for the header, plus one row per item
    assertThat(result.rows).hasSize(AYAH_BOOKMARKS_LIST.size + 1)
  }

  @Test
  fun testBookmarkObservableMixedBookmarksByDate() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(ArrayList()),
          Single.just(MIXED_BOOKMARKS_LIST),
          Single.just(ArrayList())
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    val presenter = makeBookmarkPresenter(model)
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, false)
    assertThat(tagMap).isEmpty()
    // 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(rows).hasSize(MIXED_BOOKMARKS_LIST.size + 2)
  }

  @Test
  fun testBookmarkObservableMixedBookmarksByDateWithRecentPage() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(TAG_LIST),
          Single.just(MIXED_BOOKMARKS_LIST),
          Single.just(RECENT_LIST)
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    whenever(settings.lastPage).thenReturn(42)

    val presenter = makeBookmarkPresenter(model)
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, false)
    assertThat(tagMap).hasSize(2)
    // 2 for "current page", 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(rows).hasSize(MIXED_BOOKMARKS_LIST.size + 4)
  }

  @Test
  fun testBookmarkObservableAyahBookmarksGroupedByTag() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(TAG_LIST),
          Single.just(AYAH_BOOKMARKS_LIST),
          Single.just(ArrayList())
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    val presenter = makeBookmarkPresenter(model)
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)
    assertThat(tagMap).hasSize(2)

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(rows).hasSize(
      AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1
    )
  }

  @Test
  fun testBookmarkObservableMixedBookmarksGroupedByTag() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(TAG_LIST),
          Single.just(MIXED_BOOKMARKS_LIST),
          Single.just(ArrayList())
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    val presenter = makeBookmarkPresenter(model)
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)
    assertThat(tagMap).hasSize(2)

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(rows).hasSize(
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1
    )
  }

  @Test
  fun testBookmarkObservableMixedBookmarksGroupedByTagWithRecentPage() {
    val model: BookmarkModel = object : BookmarkModel(bookmarksAdapter, recentPageModel) {
      override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
        return Single.zip(
          Single.just(TAG_LIST),
          Single.just(MIXED_BOOKMARKS_LIST),
          Single.just(RECENT_LIST)
        ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
          BookmarkData(tags, bookmarks, recentPages)
        }
      }
    }

    val presenter = makeBookmarkPresenter(model)
    val (rows, tagMap) = getBookmarkResultByDateAndValidate(presenter, true)
    assertThat(tagMap).hasSize(2)

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted, plus
    // current page header, plus current page
    assertThat(rows).hasSize(
      MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size + 1 + 2
    )
  }

  private fun makeBookmarkPresenter(model: BookmarkModel): BookmarkPresenter {
    val quranInfo = QuranInfo(MadaniDataSource())
    val quranDisplayData = QuranDisplayData(quranInfo)
    return object : BookmarkPresenter(
      appContext,
      model,
      settings,
      null,
      QuranRowFactory(quranInfo, quranDisplayData),
      quranInfo
    ) {
      public override fun subscribeToChanges() {
        // nothing
      }
    }
  }

  private fun getBookmarkResultByDateAndValidate(
    presenter: BookmarkPresenter,
    groupByTags: Boolean,
  ): BookmarkResult {
    val testObserver = presenter
      .getBookmarksListObservable(BookmarksDBAdapter.SORT_DATE_ADDED, groupByTags)
      .test()
    testObserver.awaitTerminalEvent()
    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    return testObserver.values()[0]
  }
}
