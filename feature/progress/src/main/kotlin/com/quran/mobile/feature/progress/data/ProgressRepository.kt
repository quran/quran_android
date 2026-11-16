package com.quran.mobile.feature.progress.data

import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.mobile.progress.ProgressPreferences
import com.quran.mobile.progress.history.DayMinutes
import com.quran.mobile.progress.history.LoggedSession
import com.quran.mobile.progress.history.ProgressHistory
import com.quran.mobile.progress.log.RecordedMove
import com.quran.mobile.progress.summary.BookmarkProgress
import com.quran.mobile.progress.summary.MemorizingProgress
import com.quran.mobile.progress.summary.ProgressCalculator
import com.quran.mobile.progress.summary.SalahProgress
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

/** Everything Progress shows, at one moment. */
data class ProgressSnapshot(
  val today: Long,
  val calculator: ProgressCalculator,
  /** The bookmarks that are on, in slot order. */
  val bookmarks: List<BookmarkProgress>,
  val memorizing: MemorizingProgress,
  val salah: SalahProgress,
  /** Every session, oldest first. */
  val sessions: List<LoggedSession>,
  /** Reading today not credited to a bookmark yet. */
  val uncreditedTodayMs: Long,
)

/** Puts together what Progress kept with the bookmarks and their settings, as they change. */
@SingleIn(AppScope::class)
class ProgressRepository @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val slotSettings: ReadingBookmarkSlotSettings,
  private val history: ProgressHistory,
  private val settings: Settings,
  private val quranInfoProvider: () -> QuranInfo,
  private val clock: ProgressClock,
  private val preferences: ProgressPreferences,
) {
  private data class Kept(
    val bookmarks: List<ReadingBookmark>,
    val settings: List<ReadingBookmarkSlotSetting>,
    val moves: Map<String, List<RecordedMove>>,
    val minutes: List<DayMinutes>,
    val sessions: List<LoggedSession>,
  )

  fun snapshots(): Flow<ProgressSnapshot> {
    val kept = combine(
      readingBookmarksDao.readingBookmarksFlow(),
      slotSettings.settingsFlow(),
      history.moves(),
      history.minutes(),
      history.sessions(),
    ) { bookmarks, settings, moves, minutes, sessions -> Kept(bookmarks, settings, moves, minutes, sessions) }
    // the clock (a new day, or a debug build's moved clock) changes what's shown too
    val today = combine(minutes(), preferences.changes) { _, _ -> clock.today() }.distinctUntilChanged()
    return combine(kept, today) { it, day -> build(it, day) }.flowOn(Dispatchers.Default)
  }

  suspend fun snapshot(): ProgressSnapshot = snapshots().first()

  private suspend fun build(kept: Kept, today: Long): ProgressSnapshot {
    val calculator = ProgressCalculator(quranInfoProvider(), settings.pageType())
    val bySlot = kept.bookmarks.associateBy { it.slot }
    val bookmarks = kept.settings.filter { it.enabled }.map { setting ->
      val bookmark = bySlot[setting.slot] ?: EmptyReadingBookmark(setting.slot, Instant.DISTANT_PAST)
      calculator.bookmark(bookmark, setting, kept.moves[setting.slot.name].orEmpty(), kept.minutes, today)
    }
    return ProgressSnapshot(
      today = today,
      calculator = calculator,
      bookmarks = bookmarks,
      memorizing = calculator.memorizing(kept.sessions, today),
      salah = calculator.salah(kept.sessions, today),
      sessions = kept.sessions,
      uncreditedTodayMs = kept.minutes.filter { it.slot == null && it.day == today }.sumOf { it.ms },
    )
  }

  private fun minutes(): Flow<Long> = flow {
    while (true) {
      emit(System.currentTimeMillis() / 60_000L)
      delay(60_000L)
    }
  }
}
