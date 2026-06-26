package com.quran.labs.androidquran.presenter.bookmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeTagBookmarkDialog
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
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
    bookmarksDao.setTags(listOf(Tag(TAG_ID, "Test")))
  }

  @Test
  fun `change only saves explicitly for bookmark ids`() {
    runBlocking {
      bookmarksDao.setBookmarks(listOf(Bookmark(BOOKMARK_ID, 6, 76, 137)))
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setBookmarksMode(arrayOf(BOOKMARK_ID))
      assertThat(presenter.toggleTag(TAG_ID)).isTrue()
      assertThat(bookmarksDao.getBookmarkTagIds(BOOKMARK_ID)).isEmpty()

      presenter.saveChanges()

      assertThat(bookmarksDao.getBookmarkTagIds(BOOKMARK_ID)).containsExactly(TAG_ID)
    }
  }

  @Test
  fun `change saves immediately for ayah bookmark mode`() {
    runBlocking {
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setAyahBookmarkMode(6, 76, 137)
      assertThat(presenter.toggleTag(TAG_ID)).isTrue()

      val bookmarks = bookmarksDao.bookmarks(BookmarkSortOrder.SORT_DATE_ADDED)
      assertThat(bookmarks).hasSize(1)
      assertThat(bookmarks.single().sura).isEqualTo(6)
      assertThat(bookmarks.single().ayah).isEqualTo(76)
      assertThat(bookmarks.single().tags).containsExactly(TAG_ID)
    }
  }

  @Test
  fun `single bookmark mode replaces existing tags`() {
    runBlocking {
      bookmarksDao.setTags(listOf(Tag(TAG_ID, "First"), Tag(SECOND_TAG_ID, "Second")))
      bookmarksDao.setBookmarks(listOf(Bookmark(BOOKMARK_ID, 6, 76, 137, tags = listOf(TAG_ID))))
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.setBookmarksMode(arrayOf(BOOKMARK_ID))
      assertThat(presenter.toggleTag(TAG_ID)).isFalse()
      assertThat(presenter.toggleTag(SECOND_TAG_ID)).isTrue()
      presenter.saveChanges()

      assertThat(bookmarksDao.getBookmarkTagIds(BOOKMARK_ID)).containsExactly(SECOND_TAG_ID)
    }
  }

  @Test
  fun `new tag row opens add dialog`() {
    val bookmarkDialog = FakeTagBookmarkDialog()
    val presenter = TagBookmarkPresenter(bookmarksDao)

    presenter.bind(bookmarkDialog)

    presenter.addTag()

    assertThat(bookmarkDialog.showAddTagDialogCallCount).isEqualTo(1)

    presenter.unbind(bookmarkDialog)
  }

  @Test
  fun `tag id matching add row marker remains selectable`() {
    runBlocking {
      bookmarksDao.setTags(listOf(Tag(ADD_TAG_MARKER_COLLISION_ID, "Synced Tag")))
      bookmarksDao.setBookmarks(listOf(Bookmark(BOOKMARK_ID, 6, 76, 137)))
      val bookmarkDialog = FakeTagBookmarkDialog()
      val presenter = TagBookmarkPresenter(bookmarksDao)

      presenter.bind(bookmarkDialog)
      presenter.setBookmarksMode(arrayOf(BOOKMARK_ID))

      assertThat(presenter.toggleTag(ADD_TAG_MARKER_COLLISION_ID)).isTrue()
      presenter.saveChanges()

      assertThat(bookmarkDialog.showAddTagDialogCallCount).isEqualTo(0)
      assertThat(bookmarksDao.getBookmarkTagIds(BOOKMARK_ID)).containsExactly(ADD_TAG_MARKER_COLLISION_ID)

      presenter.unbind(bookmarkDialog)
    }
  }

  @Test
  fun `add tag row is position based and not a tag id`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val adapter = TagBookmarkDialog.TagsAdapter(context, TagBookmarkPresenter(bookmarksDao))

    adapter.setData(listOf(Tag(ADD_TAG_MARKER_COLLISION_ID, "Synced Tag")), hashSetOf())

    assertThat(adapter.count).isEqualTo(2)
    assertThat(adapter.isAddTagPosition(0)).isFalse()
    assertThat(adapter.getItem(0)?.id).isEqualTo(ADD_TAG_MARKER_COLLISION_ID)
    assertThat(adapter.isAddTagPosition(1)).isTrue()
    assertThat(adapter.getItem(1)).isNull()
  }

  private companion object {
    private const val BOOKMARK_ID = "bookmark-1"
    private const val TAG_ID = "tag-1"
    private const val SECOND_TAG_ID = "tag-2"
    private const val ADD_TAG_MARKER_COLLISION_ID = "__quran_add_tag__"
  }
}
