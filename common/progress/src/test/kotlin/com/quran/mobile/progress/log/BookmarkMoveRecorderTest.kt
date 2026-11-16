package com.quran.mobile.progress.log

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.PageAppearance
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.progress.db.ProgressDatabase
import com.quran.mobile.progress.reading.PageTime
import com.quran.mobile.progress.reading.ReadingTimeLog
import com.quran.mobile.progress.time.LocalDays
import com.quran.mobile.progress.time.ProgressClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.TimeZone
import kotlin.time.Instant

class BookmarkMoveRecorderTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ProgressDatabase.Schema.create(it) }
  private val database = ProgressDatabase(driver)
  private val clock = FakeClock()
  private val settings = FakeSettings()
  private val quranInfo = QuranInfo(MadaniDataSource())

  private fun recorder() =
    BookmarkMoveRecorder(NoReadingBookmarks, settings, { quranInfo }, database, clock, AppCoroutineScope())

  private fun page(slot: ReadingBookmarkType, page: Int) =
    PageReadingBookmark(slot, page, Instant.fromEpochMilliseconds(clock.now))

  private fun rows(slot: ReadingBookmarkType = ReadingBookmarkType.TEAL) =
    database.bookmarkMoveQueries.movesFor(slot.name).executeAsList().map { it.toRecorded() }

  @Test
  fun theFirstPositionSeenIsABaselineAndTheSameAgainWritesNothing() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    val rows = rows()
    assertThat(rows.map { it.kind }).containsExactly(MoveKind.BASELINE)
    assertThat(rows.single().position).isEqualTo(BookmarkPosition(262, "madani", 15, 1))
  }

  @Test
  fun aMoveIsKeptWithItsDay() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    clock.now += LocalDays.DAY_MS
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 280)))
    val rows = rows()
    assertThat(rows.map { it.kind }).containsExactly(MoveKind.BASELINE, MoveKind.MOVE).inOrder()
    assertThat(rows[1].day).isEqualTo(rows[0].day + 1)
  }

  @Test
  fun aMoveCreditsTheReadingTimeBeforeItToThatBookmark() = runTest {
    val recorder = recorder()
    val log = ReadingTimeLog(database)
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262), page(ReadingBookmarkType.ORANGE, 500)))
    log.add(listOf(PageTime(clock.today(), 262, 600_000, read = true)), "madani", clock.now)
    clock.now += 60_000
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 270), page(ReadingBookmarkType.ORANGE, 500)))
    // read after the move: not credited yet
    clock.now += 60_000
    log.add(listOf(PageTime(clock.today(), 270, 120_000, read = true)), "madani", clock.now)

    val minutes = database.readingTimeQueries.minutesBySlotAndDay().executeAsList().associate { it.slot to it.ms }
    assertThat(minutes).containsExactly("TEAL", 600_000L, null, 120_000L)
  }

  @Test
  fun aClearedBookmarkPlacedAgainIsAMoveFromWhereItWas() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    recorder.record(listOf(EmptyReadingBookmark(ReadingBookmarkType.TEAL, Instant.fromEpochMilliseconds(clock.now))))
    recorder.record(listOf(EmptyReadingBookmark(ReadingBookmarkType.TEAL, Instant.fromEpochMilliseconds(clock.now))))
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 270)))
    assertThat(rows().map { it.kind }).containsExactly(MoveKind.BASELINE, MoveKind.CLEAR, MoveKind.MOVE).inOrder()
  }

  @Test
  fun anAyahBookmarkIsOnItsPage() = runTest {
    recorder().record(listOf(AyahReadingBookmark(ReadingBookmarkType.RED, 18, 1, Instant.fromEpochMilliseconds(clock.now))))
    assertThat(rows(ReadingBookmarkType.RED).single().position)
      .isEqualTo(BookmarkPosition(quranInfo.getPageFromSuraAyah(18, 1), "madani", 18, 1))
  }

  @Test
  fun aRecorderStartedAgainWritesNothingForWhatItAlreadyKept() = runTest {
    recorder().record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    recorder().record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    assertThat(rows()).hasSize(1)
  }

  @Test
  fun anotherPageTypeIsABaseline() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    settings.type = "warsh"
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 250)))
    assertThat(rows().map { it.kind }).containsExactly(MoveKind.BASELINE, MoveKind.BASELINE).inOrder()
  }

  @Test
  fun aMoveMadeEarlierOnAnotherDeviceKeepsItsTime() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    val movedAt = clock.now + 60_000
    clock.now += 3_600_000
    recorder.record(listOf(PageReadingBookmark(ReadingBookmarkType.TEAL, 280, Instant.fromEpochMilliseconds(movedAt))))
    assertThat(rows()[1].atMs).isEqualTo(movedAt)
  }

  @Test
  fun aCycleStartedByHandIsKept() = runTest {
    val recorder = recorder()
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    recorder.markCycleStart(ReadingBookmarkType.TEAL.name)
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 262)))
    recorder.record(listOf(page(ReadingBookmarkType.TEAL, 263)))
    assertThat(rows().map { it.kind }).containsExactly(MoveKind.BASELINE, MoveKind.CYCLE_MARK, MoveKind.MOVE).inOrder()
  }

  private class FakeClock : ProgressClock {
    var now = 20_000L * LocalDays.DAY_MS + 9 * 3_600_000L
    override fun nowMs() = now
    override fun zone(): TimeZone = TimeZone.getTimeZone("UTC")
  }

  private object NoReadingBookmarks : ReadingBookmarksDao {
    override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> = emptyFlow()
    override suspend fun readingBookmarks(): List<ReadingBookmark> = emptyList()
    override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int) = error("unused")
    override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah) = error("unused")
    override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark = error("unused")
    override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) = error("unused")
    override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int) = error("unused")
    override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int) = error("unused")
    override suspend fun updateReadingBookmarks(ayah: SuraAyah, added: List<ReadingBookmark>, removed: List<ReadingBookmark>) =
      error("unused")
  }

  private class FakeSettings : Settings {
    var type = "madani"
    override suspend fun pageType() = type
    override suspend fun setVersion(version: Int) = Unit
    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = Unit
    override suspend fun lastPage() = 1
    override suspend fun pageAppearance(): PageAppearance = error("unused")
    override suspend fun pageTextBrightness() = 0
    override suspend fun nightModeBackgroundBrightness() = 0
    override suspend fun dayPageBackgroundColor(): Int? = null
    override suspend fun shouldShowHeaderFooter() = false
    override suspend fun shouldShowBookmarks() = false
    override suspend fun setPageType(pageType: String) = Unit
    override suspend fun showSidelines() = false
    override suspend fun setShowSidelines(show: Boolean) = Unit
    override suspend fun showLineDividers() = false
    override suspend fun setShouldShowLineDividers(show: Boolean) = Unit
    override suspend fun setAyahTextSize(value: Int) = Unit
    override suspend fun translationTextSize() = 0
    override fun preferencesFlow(): Flow<String> = emptyFlow()
  }
}
