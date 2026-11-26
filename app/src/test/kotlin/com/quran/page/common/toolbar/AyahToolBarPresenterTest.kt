package com.quran.page.common.toolbar

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AyahToolBarPresenterTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var bookmarksDao: FakeBookmarksDao
  private lateinit var readingEventPresenter: ReadingEventPresenter
  private lateinit var presenter: AyahToolBarPresenter

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    bookmarksDao = FakeBookmarksDao()
    readingEventPresenter = ReadingEventPresenter(QuranInfo(MadaniDataSource()))
    presenter = AyahToolBarPresenter.forTest(bookmarksDao, readingEventPresenter, testDispatcher)
  }

  @After
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `does not react before bind, cancels on unbind, and supports rebinding`() = runTest {
    val reactor = FakeReactor()
    val selection = AyahSelection.Ayah(
      SuraAyah(2, 255),
      SelectionIndicator.SelectedItemPosition(
        SelectionRectangle(0f, 0f, 10f, 10f),
        SelectionRectangle(0f, 0f, 10f, 10f)
      )
    )

    // Before bind, selections are not pushed to reactor
    readingEventPresenter.onAyahSelection(selection)
    assertThat(reactor.selectionIndicators).isEmpty()

    // Bind updates selection and bookmark status
    bookmarksDao.setBookmarks(listOf(Bookmark("b-1", 2, 255, 42, 100)))
    presenter.bind(reactor)
    advanceUntilIdle()
    assertThat(reactor.selectionIndicators).hasSize(1)
    assertThat(reactor.bookmarkStatuses).containsExactly(true)

    // Unbind stops observing selection changes
    presenter.unbind(reactor)
    val secondSelection = AyahSelection.Ayah(
      SuraAyah(1, 1),
      SelectionIndicator.SelectedItemPosition(
        SelectionRectangle(1f, 1f, 10f, 10f),
        SelectionRectangle(1f, 1f, 10f, 10f)
      )
    )
    readingEventPresenter.onAyahSelection(secondSelection)
    advanceUntilIdle()
    assertThat(reactor.selectionIndicators).hasSize(1)

    // Re-bind collects both ayahSelectionFlow and bookmark channel again without failing
    presenter.bind(reactor)
    advanceUntilIdle()
    assertThat(reactor.selectionIndicators).hasSize(2)
    assertThat(reactor.bookmarkStatuses).containsExactly(true, false).inOrder()
  }

  private class FakeReactor : AyahSelectionReactor {
    val selectionIndicators = mutableListOf<SelectionIndicator>()
    val bookmarkStatuses = mutableListOf<Boolean>()

    override fun onSelectionChanged(selectionIndicator: SelectionIndicator, reset: Boolean) {
      selectionIndicators.add(selectionIndicator)
    }

    override fun updateBookmarkStatus(isBookmarked: Boolean) {
      bookmarkStatuses.add(isBookmarked)
    }
  }
}
