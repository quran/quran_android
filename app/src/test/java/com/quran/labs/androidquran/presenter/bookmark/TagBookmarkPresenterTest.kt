package com.quran.labs.androidquran.presenter.bookmark

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.CountDownLatch

class TagBookmarkPresenterTest {

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
    bookmarkModel = object : BookmarkModel(mock(), mock()), TagsObservableReporter {
      var tagsObservableCalled: Int = 0

      override val tagsObservable: Single<List<Tag>>
        get() = tagsObservableInternal()

      override fun tagsObservableCalled(): Int = tagsObservableCalled

      private fun tagsObservableInternal(): Single<List<Tag>> {
        tagsObservableCalled++
        return Single.just(listOf(Tag(1, "Test")))
      }

      override fun tagsObservable(): Observable<Boolean> {
        return Observable.empty()
      }

      override fun getBookmarkTagIds(bookmarkIdSingle: Single<Long>): Maybe<List<Long>> {
        return Maybe.empty()
      }
    }
  }

  @Test
  @Throws(InterruptedException::class)
  fun testTagRefresh() {
    val latch = CountDownLatch(1)
    val secondLatch = CountDownLatch(2)

    val tagPresenter: TagBookmarkPresenter = spy(object : TagBookmarkPresenter(bookmarkModel) {
      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        latch.countDown()
        secondLatch.countDown()
      }
    })

    val presenter = object : TagBookmarkPresenter(bookmarkModel), RefreshReporter {
      var refreshesCalled: Int = 0

      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        tagPresenter.onRefreshedData(data)
      }

      override fun refreshesCalled(): Int = refreshesCalled

      override fun refresh() {
        refreshesCalled++
        return tagPresenter.refresh()
      }
    }

    presenter.setBookmarksMode(longArrayOf(1))
    latch.await()

    presenter.setAyahBookmarkMode(6, 76, 137)
    secondLatch.await()

    // make sure we called refresh twice
    assertThat(presenter.refreshesCalled()).isEqualTo(2)

    // but make sure we only queried tags from the database once
    val tagsReporter = bookmarkModel as TagsObservableReporter
    assertThat(tagsReporter.tagsObservableCalled()).isEqualTo(1)
  }

  @Test
  @Throws(InterruptedException::class)
  fun testChangeShouldOnlySaveExplicitlyForBookmarkIds() {
    val bookmarkModel = object : BookmarkModel(mock(), mock()) {
      override fun updateBookmarkTags(
        bookmarkIds: LongArray,
        tagIds: Set<Long>,
        deleteNonTagged: Boolean
      ): Observable<Boolean> {
        return Observable.just(true)
      }

      override val tagsObservable: Single<List<Tag>>
        get() = Single.just(listOf(Tag(1, "Test")))

      override fun tagsObservable(): Observable<Boolean> {
        return Observable.empty()
      }

      override fun getBookmarkTagIds(bookmarkIdSingle: Single<Long>): Maybe<List<Long>> {
        return Maybe.empty()
      }
    }

    val saveLatch = CountDownLatch(1)
    val refreshLatch = CountDownLatch(1)

    val presenter: TagBookmarkPresenter = object : TagBookmarkPresenter(bookmarkModel), SavesCalledReporter {
      var savesCalled: Int = 0

      override fun savesCalled(): Int = savesCalled

      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        refreshLatch.countDown()
      }

      override fun saveChanges() {
        savesCalled++
        // override because we aren't testing the save process here, just that save is called
        saveLatch.countDown()
      }
    }

    presenter.setBookmarksMode(longArrayOf(1))
    refreshLatch.await()

    // try to modify a tag - save shouldn't be called
    assertThat(presenter.toggleTag(1)).isTrue()

    val savingPresenter = presenter as SavesCalledReporter
    assertThat(savingPresenter.savesCalled()).isEqualTo(0)

    // explicitly call save
    presenter.saveChanges()
    saveLatch.countDown()
    assertThat(savingPresenter.savesCalled()).isEqualTo(1)
  }

  @Test
  @Throws(InterruptedException::class)
  fun testChangeShouldSaveImmediatelyForAyahBookmarks() {
    val bookmarkModel = object : BookmarkModel(mock(), mock()), UpdatedBookmarkTagsReporter {
      var updateBookmarkTagsCalled: Int = 0

      override fun updateBookmarkTags(
        bookmarkIds: LongArray,
        tagIds: Set<Long>,
        deleteNonTagged: Boolean
      ): Observable<Boolean> {
        updateBookmarkTagsCalled++
        return Observable.just(true)
      }

      override val tagsObservable: Single<List<Tag>>
        get() = Single.just(listOf(Tag(1, "Test")))

      override fun tagsObservable(): Observable<Boolean> {
        return Observable.empty()
      }

      override fun getBookmarkTagIds(bookmarkIdSingle: Single<Long>): Maybe<List<Long>> {
        return Maybe.empty()
      }

      override fun safeAddBookmark(sura: Int?, ayah: Int?, page: Int): Observable<Long> {
        return Observable.just(2L)
      }

      override fun updatedBookmarkTagsCalled(): Int = updateBookmarkTagsCalled
    }

    // when refresh is done
    val refreshLatch = CountDownLatch(1)

    // save latches
    val latch = CountDownLatch(1)
    val secondLatch = CountDownLatch(2)

    val presenter: TagBookmarkPresenter = object : TagBookmarkPresenter(bookmarkModel), SavesCalledReporter {
      var savesCalled: Int = 0

      override fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
        super.onRefreshedData(data)
        refreshLatch.countDown()
      }

      override fun onSaveChangesDone() {
        savesCalled++
        super.onSaveChangesDone()
        latch.countDown()
        secondLatch.countDown()
      }

      override fun savesCalled(): Int = savesCalled
    }

    presenter.setAyahBookmarkMode(6, 76, 137)
    // make sure refresh is done first
    refreshLatch.await()

    // switch tag and wait for the save
    assertThat(presenter.toggleTag(1)).isTrue()
    latch.await()

    val savesPresenter = presenter as SavesCalledReporter
    assertThat(savesPresenter.savesCalled()).isEqualTo(1)

    // try to save again (should do nothing)
    presenter.saveChanges()
    secondLatch.await()

    val updateReportingBookmarkModel = bookmarkModel as UpdatedBookmarkTagsReporter
    assertThat(updateReportingBookmarkModel.updatedBookmarkTagsCalled()).isEqualTo(1)
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

  private interface TagsObservableReporter {
    fun tagsObservableCalled(): Int
  }

  private interface SavesCalledReporter {
    fun savesCalled(): Int
  }

  private interface UpdatedBookmarkTagsReporter {
    fun updatedBookmarkTagsCalled(): Int
  }

  private interface RefreshReporter {
    fun refreshesCalled(): Int
  }
}
