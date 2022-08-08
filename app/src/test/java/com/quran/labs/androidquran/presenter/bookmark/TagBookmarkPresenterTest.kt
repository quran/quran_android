package com.quran.labs.androidquran.presenter.bookmark

import androidx.core.util.Pair
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when` as whenever

class TagBookmarkPresenterTest {

  @Mock
  private lateinit var bookmarkModel: BookmarkModel

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.io() }
    }
  }

  @Before
  fun setupTest() {
    MockitoAnnotations.openMocks(this@TagBookmarkPresenterTest)

    val tags: MutableList<Tag> = ArrayList()
    tags.add(Tag(1, "Test"))
    whenever(bookmarkModel.tagsObservable()).thenReturn(Observable.empty())
    whenever(bookmarkModel.tagsObservable).thenReturn(Single.just(tags))
    whenever(bookmarkModel.getBookmarkTagIds(any())).thenReturn(Maybe.empty())
  }

  @Test
  @Throws(InterruptedException::class)
  fun testTagRefresh() {
    val latch = CountDownLatch(1)
    val secondLatch = CountDownLatch(2)

    val presenter: TagBookmarkPresenter = spy(object : TagBookmarkPresenter(bookmarkModel) {
      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        latch.countDown()
        secondLatch.countDown()
      }
    })

    presenter.setBookmarksMode(longArrayOf(1))
    latch.await()

    presenter.setAyahBookmarkMode(6, 76, 137)
    secondLatch.await()

    // make sure we called refresh twice
    verify(presenter, times(2)).refresh()

    // but make sure we only queried tags from the database once
    verify(bookmarkModel, times(1)).tagsObservable
  }

  @Test
  @Throws(InterruptedException::class)
  fun testChangeShouldOnlySaveExplicitlyForBookmarkIds() {
    whenever(bookmarkModel.updateBookmarkTags(any(LongArray::class.java), any(), anyBoolean()))
      .thenReturn(Observable.just(true))

    val saveLatch = CountDownLatch(1)
    val refreshLatch = CountDownLatch(1)
    val presenter: TagBookmarkPresenter = spy(object : TagBookmarkPresenter(bookmarkModel) {
      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        refreshLatch.countDown()
      }

      override fun saveChanges() {
        // override because we aren't testing the save process here, just that save is called
        saveLatch.countDown()
      }
    })

    presenter.setBookmarksMode(longArrayOf(1))
    refreshLatch.await()

    // try to modify a tag - save shouldn't be called
    assertThat(presenter.toggleTag(1)).isTrue()
    verify(presenter, times(0)).saveChanges()

    // explicitly call save
    presenter.saveChanges()
    saveLatch.countDown()
    verify(presenter, times(1)).saveChanges()
  }

  @Test
  @Throws(InterruptedException::class)
  fun testChangeShouldSaveImmediatelyForAyahBookmarks() {
    whenever(bookmarkModel.updateBookmarkTags(any(LongArray::class.java), any(), anyBoolean()))
      .thenReturn(Observable.just(true))

    whenever(bookmarkModel.safeAddBookmark(anyInt(), anyInt(), anyInt()))
      .thenReturn(Observable.just(2L))

    // when refresh is done
    val refreshLatch = CountDownLatch(1)

    // save latches
    val latch = CountDownLatch(1)
    val secondLatch = CountDownLatch(2)

    val presenter: TagBookmarkPresenter = spy(object : TagBookmarkPresenter(bookmarkModel) {
      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        refreshLatch.countDown()
      }

      override fun onSaveChangesDone() {
        super.onSaveChangesDone()
        latch.countDown()
        secondLatch.countDown()
      }
    })

    presenter.setAyahBookmarkMode(6, 76, 137)
    // make sure refresh is done first
    refreshLatch.await()

    // switch tag and wait for the save
    assertThat(presenter.toggleTag(1)).isTrue()
    latch.await()

    verify(presenter, times(1)).saveChanges()

    // try to save again (should do nothing)
    presenter.saveChanges()
    secondLatch.await()

    verify(bookmarkModel, times(1))
      .updateBookmarkTags(any(LongArray::class.java), any(), anyBoolean())
  }

  @Test
  fun testAddDialogCall() {
    val bookmarkDialog = mock(TagBookmarkDialog::class.java)

    val presenter = spy(TagBookmarkPresenter(bookmarkModel))
    presenter.bind(bookmarkDialog)
    assertThat(presenter.toggleTag(-1)).isFalse()
    verify(bookmarkDialog, times(1)).showAddTagDialog()
    presenter.unbind(bookmarkDialog)
  }

  @Test
  fun testAddDialogCallUnbound() {
    val presenter = spy(TagBookmarkPresenter(bookmarkModel))
    assertThat(presenter.toggleTag(-1)).isFalse()
    verify(presenter, times(0)).setMadeChanges()
  }
}
