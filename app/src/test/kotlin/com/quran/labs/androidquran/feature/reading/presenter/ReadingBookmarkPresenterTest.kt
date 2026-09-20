package com.quran.labs.androidquran.feature.reading.presenter

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlin.time.Instant

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

  private fun pageBookmark(page: Int, slot: ReadingBookmarkType = ReadingBookmarkType.TEAL) =
    PageReadingBookmark(slot, page, Instant.fromEpochSeconds(1))

  private fun ayahBookmark(
    sura: Int,
    ayah: Int,
    slot: ReadingBookmarkType = ReadingBookmarkType.TEAL
  ) = AyahReadingBookmark(slot, sura, ayah, Instant.fromEpochSeconds(1))

  // writes run on appCoroutineScope, which is backed by the real Dispatchers.IO rather than the
  // test's virtual-time dispatcher, so it isn't advanced by testDispatcher.scheduler - give it a
  // moment to actually complete instead
  private fun waitForRealDispatch() = runBlocking { delay(100) }

  @Test
  fun `page icon is selected for exact page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(42))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(true)
  }

  @Test
  fun `page icon is not selected for a different page reading bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(43))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `page icon is not selected for ayah reading bookmark on current page`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(ayahBookmark(2, 255))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(false)
  }

  @Test
  fun `page icon is selected when any of the three pins is on this page`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(
      pageBookmark(10, ReadingBookmarkType.TEAL),
      pageBookmark(42, ReadingBookmarkType.INDIGO)
    )
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(screen.pageBookmarkStates).containsExactly(true)
  }

  @Test
  fun `placing a pin on a page writes it immediately`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(10, ReadingBookmarkType.CORAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.CORAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks())
      .containsExactly(pageBookmark(42, ReadingBookmarkType.CORAL))
  }

  @Test
  fun `placing a pin on an ayah writes an ayah bookmark`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(
      ReadingBookmarkType.INDIGO,
      ReadingBookmarkTarget.Ayah(SuraAyah(4, 6))
    )
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks())
      .containsExactly(ayahBookmark(4, 6, ReadingBookmarkType.INDIGO))
  }

  @Test
  fun `placing a pin where it already sits changes nothing and says nothing`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(42, ReadingBookmarkType.TEAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.TEAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()

    assertThat(screen.changes).isEmpty()
    assertThat(readingBookmarksDao.readingBookmarks())
      .containsExactly(pageBookmark(42, ReadingBookmarkType.TEAL))
  }

  @Test
  fun `the toast names the pin and where it moved from`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(10, ReadingBookmarkType.CORAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.CORAL, ReadingBookmarkTarget.Page(42))

    val change = screen.changes.single()
    assertThat(change).isInstanceOf(ReadingBookmarkChange.Placed::class.java)
    assertThat(change.slot).isEqualTo(ReadingBookmarkType.CORAL)
    assertThat(change.previous).isEqualTo(pageBookmark(10, ReadingBookmarkType.CORAL))
    assertThat((change as ReadingBookmarkChange.Placed).target)
      .isEqualTo(ReadingBookmarkTarget.Page(42))
  }

  @Test
  fun `the first ever placement gets the education toast, later ones do not`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.TEAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.TEAL, ReadingBookmarkTarget.Page(43))

    assertThat(screen.isEducation).containsExactly(true, false).inOrder()
  }

  @Test
  fun `clearing a pin removes it and offers to put it back`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(42, ReadingBookmarkType.TEAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.clearReadingBookmark(ReadingBookmarkType.TEAL)
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks()).isEmpty()
    assertThat(screen.changes.single())
      .isEqualTo(
        ReadingBookmarkChange.Cleared(
          ReadingBookmarkType.TEAL,
          pageBookmark(42, ReadingBookmarkType.TEAL)
        )
      )
  }

  @Test
  fun `clearing a pin that isn't placed does nothing`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.clearReadingBookmark(ReadingBookmarkType.TEAL)
    waitForRealDispatch()

    assertThat(screen.changes).isEmpty()
  }

  @Test
  fun `undo puts the pin back where it was`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(10, ReadingBookmarkType.CORAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.CORAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks())
      .containsExactly(pageBookmark(10, ReadingBookmarkType.CORAL))
    assertThat(screen.dismissCount).isEqualTo(1)
  }

  @Test
  fun `undo of a first placement takes the pin back off`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.TEAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks()).isEmpty()
  }

  @Test
  fun `undo of a clear puts the pin back`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(ayahBookmark(4, 6, ReadingBookmarkType.TEAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.clearReadingBookmark(ReadingBookmarkType.TEAL)
    waitForRealDispatch()

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks())
      .containsExactly(ayahBookmark(4, 6, ReadingBookmarkType.TEAL))
  }

  @Test
  fun `undo refuses to clobber a change that landed after ours`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao(pageBookmark(10, ReadingBookmarkType.TEAL))
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.TEAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()

    // something else (the ayah sheet, a synced change from another device) moves the same pin
    // while the toast is still up
    val newerBookmark = ayahBookmark(2, 255, ReadingBookmarkType.TEAL)
    readingBookmarksDao.setReadingBookmark(newerBookmark)

    screen.undoActions.last().invoke()
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks()).containsExactly(newerBookmark)
  }

  @Test
  fun `moving a second pin leaves the first one alone`() = runTest {
    val readingBookmarksDao = FakeReadingBookmarksDao()
    val screen = RecordingScreen()
    val presenter = presenter(readingBookmarksDao)

    presenter.bind(flowOf(42), screen)
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.CORAL, ReadingBookmarkTarget.Page(42))
    waitForRealDispatch()
    testDispatcher.scheduler.advanceUntilIdle()
    presenter.placeReadingBookmark(ReadingBookmarkType.INDIGO, ReadingBookmarkTarget.Page(43))
    waitForRealDispatch()

    assertThat(readingBookmarksDao.readingBookmarks()).containsExactly(
      pageBookmark(42, ReadingBookmarkType.CORAL),
      pageBookmark(43, ReadingBookmarkType.INDIGO)
    )
  }

  private class RecordingScreen : ReadingBookmarkPresenter.Screen {
    val pageBookmarkStates = mutableListOf<Boolean>()
    val changes = mutableListOf<ReadingBookmarkChange>()
    val isEducation = mutableListOf<Boolean>()
    val undoActions = mutableListOf<() -> Unit>()
    val dismissActions = mutableListOf<() -> Unit>()
    var dismissCount = 0

    override fun setPageReadingBookmarkSelected(isBookmarked: Boolean) {
      pageBookmarkStates += isBookmarked
    }

    override fun showReadingBookmarkChangedToast(
      change: ReadingBookmarkChange,
      isEducation: Boolean,
      onUndo: () -> Unit,
      onDismiss: () -> Unit
    ) {
      changes += change
      this.isEducation += isEducation
      undoActions += onUndo
      dismissActions += onDismiss
    }

    override fun dismissReadingBookmarkChangedToast() {
      dismissCount++
    }
  }
}
