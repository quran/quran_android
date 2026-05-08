package com.quran.labs.androidquran.presenter.bookmark

import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeTagBookmarkDialog
import com.quran.labs.test.RxSchedulerRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TagBookmarkPresenterTest {

  @get:Rule
  val rxRule = RxSchedulerRule()

  private lateinit var bookmarksDao: FakeBookmarksDao

  @Before
  fun setupTest() {
    bookmarksDao = FakeBookmarksDao()
    bookmarksDao.setTags(listOf(Tag(1, "Test")))
  }

  @Test
  fun `change only saves explicitly for bookmark ids`() {
    runBlocking {
      bookmarksDao.setBookmarks(listOf(Bookmark(1, 6, 76, 137)))
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setBookmarksMode(longArrayOf(1))
      assertThat(presenter.toggleTag(1)).isTrue()
      assertThat(bookmarksDao.getBookmarkTagIds(1)).isEmpty()

      presenter.saveChanges()

      assertThat(bookmarksDao.getBookmarkTagIds(1)).containsExactly(1L)
    }
  }

  @Test
  fun `change saves immediately for ayah bookmark mode`() {
    runBlocking {
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setAyahBookmarkMode(6, 76, 137)
      assertThat(presenter.toggleTag(1)).isTrue()

      val bookmarks = bookmarksDao.bookmarks(BookmarkSortOrder.SORT_DATE_ADDED)
      assertThat(bookmarks).hasSize(1)
      assertThat(bookmarks.single().sura).isEqualTo(6)
      assertThat(bookmarks.single().ayah).isEqualTo(76)
      assertThat(bookmarks.single().tags).containsExactly(1L)
    }
  }

  @Test
  fun `single bookmark mode replaces existing tags`() {
    runBlocking {
      bookmarksDao.setTags(listOf(Tag(1, "First"), Tag(2, "Second")))
      bookmarksDao.setBookmarks(listOf(Bookmark(1, 6, 76, 137, tags = listOf(1))))
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setBookmarksMode(longArrayOf(1))
      assertThat(presenter.toggleTag(1)).isFalse()
      assertThat(presenter.toggleTag(2)).isTrue()
      presenter.saveChanges()

      assertThat(bookmarksDao.getBookmarkTagIds(1)).containsExactly(2L)
    }
  }

  @Test
  fun `new tag row opens add dialog`() {
    val bookmarkDialog = FakeTagBookmarkDialog()
    val presenter = TagBookmarkPresenter(bookmarksDao)

    presenter.bind(bookmarkDialog)

    assertThat(presenter.toggleTag(-1)).isFalse()
    assertThat(bookmarkDialog.showAddTagDialogCallCount).isEqualTo(1)

    presenter.unbind(bookmarkDialog)
  }
}
