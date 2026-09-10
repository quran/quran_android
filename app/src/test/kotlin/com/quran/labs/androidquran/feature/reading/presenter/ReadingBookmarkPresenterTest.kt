package com.quran.labs.androidquran.feature.reading.presenter

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ReadingBookmarkPresenterTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var quranSettings: QuranSettings
  private lateinit var appCoroutineScope: AppCoroutineScope

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    QuranSettings.setInstance(null)
    quranSettings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
    appCoroutineScope = AppCoroutineScope()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    QuranSettings.setInstance(null)
  }

  private fun presenter(readingBookmarksDao: FakeReadingBookmarksDao) =
    ReadingBookmarkPresenter(readingBookmarksDao, quranSettings, appCoroutineScope)

  // the actual write in confirmPendingMove/the un-bookmark path runs on appCoroutineScope, which
  // is backed by the real Dispatchers.IO rather than the test's virtual-time dispatcher, so it
  // isn't advanced by testDispatcher.scheduler - give it a moment to actually complete instead
  private fun waitForRealDispatch() = runBlocking { delay(100) }

  @Test
  fun `page icon is selected for exact page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(42, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(true)
  }

  @Test
  fun `page icon is not selected for a different page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(43, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `page icon is not selected for ayah reading bookmark on current page`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(AyahReadingBookmark(2, 255, timestamp = 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `toggle optimistically selects the page icon without writing yet`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates.last()).isTrue()
    assertThat(readingBookmarksDao.readingBookmark()).isNull()
  }

  @Test
  fun `toggle on shows moved toast with no previous bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.movedToastPreviousBookmarks).containsExactly(null)
    assertThat(screen.movedToastIsEducation).containsExactly(true)
  }

  @Test
  fun `toggle on shows moved toast with previous bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.movedToastPreviousBookmarks).containsExactly(PageReadingBookmark(10, 1))
  }

  @Test
  fun `second toggle is a short toast, not education`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()
    screen.confirmActions.last().invoke()
    waitForRealDispatch()

    presenter.togglePageReadingBookmark(43)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.movedToastIsEducation.last()).isFalse()
  }

  @Test
  fun `toggle off does not show moved toast`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(42, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.movedToastPreviousBookmarks).isEmpty()
  }

  @Test
  fun `confirm writes the pending move`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    // nothing written yet - the move is only pending until confirmed
    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(10, 1))

    screen.confirmActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(42, 1))
  }

  @Test
  fun `unbind confirms the pending move and dismisses the toast`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    presenter.unbind(screen)
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(42, 1))
    assertThat(screen.dismissCount).isEqualTo(1)
  }

  @Test
  fun `undo cancels the pending move without ever writing it`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(10, 1))
    assertThat(screen.pageBookmarkStates.last()).isFalse()
  }

  @Test
  fun `undo with no previous bookmark leaves the dao untouched`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isNull()
  }

  @Test
  fun `undo can never clobber a bookmark changed elsewhere, since it never writes`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    // something else (an ayah bookmark, a synced change from another device, etc.) changes the
    // reading bookmark while the toast/pending move is still up
    val newerBookmark = AyahReadingBookmark(2, 255, timestamp = 5)
    readingBookmarksDao.setReadingBookmark(newerBookmark)

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(newerBookmark)
  }

  @Test
  fun `re-tapping the pending page cancels instead of writing`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(10, 1))
    assertThat(screen.pageBookmarkStates.last()).isFalse()
  }

  @Test
  fun `removing the persisted bookmark while a move is pending dismisses the toast`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
    val pageFlow = MutableStateFlow(42)
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(pageFlow, screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(42)
    testDispatcher.scheduler.advanceUntilIdle()

    pageFlow.value = 10
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.togglePageReadingBookmark(10)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.dismissCount).isEqualTo(1)
  }

  @Test
  fun `moving to a third page while one move is pending chains back to the original previous bookmark`() =
    runTest {
      val readingBookmarksDao = FakeReadingBookmarksDao(PageReadingBookmark(10, 1))
      val screen = RecordingScreen()
      val presenter = presenter(readingBookmarksDao)

      presenter.bind(flowOf(42), screen)
      testDispatcher.scheduler.advanceUntilIdle()
      presenter.togglePageReadingBookmark(42)
      testDispatcher.scheduler.advanceUntilIdle()

      presenter.togglePageReadingBookmark(43)
      testDispatcher.scheduler.advanceUntilIdle()

      // page 42 was never actually written, so the second move's "previous" is still page 10
      assertThat(screen.movedToastPreviousBookmarks.last()).isEqualTo(PageReadingBookmark(10, 1))

      screen.confirmActions.last().invoke()
      waitForRealDispatch()

      assertThat(readingBookmarksDao.readingBookmark()).isEqualTo(PageReadingBookmark(43, 1))
    }

  private class RecordingScreen : ReadingBookmarkPresenter.Screen {
    val pageBookmarkStates = mutableListOf<Boolean>()
    val movedToastPreviousBookmarks = mutableListOf<ReadingBookmark?>()
    val movedToastIsEducation = mutableListOf<Boolean>()
    val undoActions = mutableListOf<() -> Unit>()
    val confirmActions = mutableListOf<() -> Unit>()
    var dismissCount = 0

    override fun setPageReadingBookmarkSelected(isBookmarked: Boolean) {
      pageBookmarkStates += isBookmarked
    }

    override fun showReadingBookmarkMovedToast(
      previousBookmark: ReadingBookmark?,
      isEducation: Boolean,
      onUndo: () -> Unit,
      onConfirm: () -> Unit
    ) {
      movedToastPreviousBookmarks += previousBookmark
      movedToastIsEducation += isEducation
      undoActions += onUndo
      confirmActions += onConfirm
    }

    override fun dismissReadingBookmarkMovedToast() {
      dismissCount++
    }
  }
}
