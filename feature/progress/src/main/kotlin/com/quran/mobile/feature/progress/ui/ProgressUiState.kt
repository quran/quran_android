package com.quran.mobile.feature.progress.ui

import androidx.compose.runtime.Immutable
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.progress.summary.Held
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

enum class ProgressSection { BOOKMARKS, MEMORIZING, SALAH, SESSIONS }

/**
 * One column of a day chart: [above] the axis, [below] it; null for a day still to come. [empty] for
 * a day with nothing to show (no session that day), drawn as just its number.
 */
@Immutable
data class DayColumn(
  val dayN: Int,
  val label: Int,
  val above: Int?,
  val below: Int?,
  val isToday: Boolean,
  val empty: Boolean = false,
)

/** Where a bookmark stands on the juz line: 0 is the start of juz 1, 30 the end of juz 30. */
@Immutable
data class JuzLineState(
  val position: Float?,
  /** Where the read part starts (the cycle's start, or where Progress first saw it). */
  val runStart: Float?,
  /** Where a steady pace is by the end of today, for a cycle with a deadline. */
  val pace: Float?,
  /** The juz the bookmark is in. */
  val current: Int?,
  /** Pages of each juz, and how many of them were read this cycle. */
  val juz: ImmutableList<JuzPages>,
)

@Immutable
data class JuzPages(val juz: Int, val firstPage: Int, val lastPage: Int, val read: Int) {
  val pages: Int get() = lastPage - firstPage + 1
}

/** Where a bookmark is, for its sentence: its page, and the sura and ayah it marks. */
@Immutable
data class BookmarkPlace(val page: Int, val suraName: String, val sura: Int, val ayah: Int)

@Immutable
data class BookmarkView(
  val slot: ReadingBookmarkType,
  val name: String,
  val purpose: ReadingBookmarkPurpose,
  val targetDays: Int,
  val place: BookmarkPlace?,
  val juz: Int?,
  /** The open cycle's start (local day), when it's known. */
  val cycleStartDay: Long?,
  /** Since when Progress has followed it, when the cycle's start isn't known. */
  val followedSinceDay: Long?,
  val dayN: Int?,
  val finishByDay: Long?,
  val overdue: Boolean,
  /** Pages ahead of a steady pace (negative when behind), for a cycle with a deadline. */
  val ahead: Int?,
  val line: JuzLineState,
  val days: ImmutableList<DayColumn>,
  /** Pages a day that finish in time, drawn as the goal line. */
  val goal: Int?,
  val averageMinutes: Int?,
  val todayMinutes: Int?,
  val cycleReadingMs: Long,
  val cyclePages: Int,
  val lastCycleDays: Int?,
  val lastCycleStartDay: Long?,
  val lastCycleEndDay: Long?,
  /** Whether it has moved since Progress kept track; if not, its days are sample ones. */
  val hasHistory: Boolean,
) {
  val placed: Boolean get() = place != null
}

@Immutable
data class MemorizingView(
  /** The juz shown first: the memorization bookmark's, else the one with the most held. */
  val focusJuz: Int?,
  val juz: ImmutableList<Held>,
  val suras: ImmutableMap<Int, ImmutableList<Held>>,
  /** Accuracy (percent) above, hints below. */
  val days: ImmutableList<DayColumn>,
  val averageAccuracy: Int?,
  val hasHistory: Boolean,
)

@Immutable
data class SalahView(
  /** Prayers followed above, hints below. */
  val days: ImmutableList<DayColumn>,
  val prayers: Int,
  val hasHistory: Boolean,
)

@Immutable
data class SessionAyahView(val suraName: String, val sura: Int, val ayah: Int, val ayahWords: Int, val words: Int, val hinted: Int, val missed: Int)

@Immutable
data class SessionView(
  val id: String,
  /** Its passage, e.g. "Al-Ikhlas 1–4". */
  val title: String,
  val day: Long,
  val startedAtMs: Long,
  val mode: String,
  val from: String,
  val to: String,
  val minutes: Int,
  val recited: Int,
  val hinted: Int,
  val missed: Int,
  val finished: Boolean,
  val recordingId: String?,
  val ayahs: ImmutableList<SessionAyahView>,
)

@Immutable
data class ReminderView(val on: Boolean, val minuteOfDay: Int, val allowed: Boolean)

@Immutable
data class ProgressUiState(
  val today: Long,
  val bookmarks: ImmutableList<BookmarkView> = persistentListOf(),
  val memorizing: MemorizingView = MemorizingView(null, persistentListOf(), persistentMapOf(), persistentListOf(), null, false),
  val salah: SalahView = SalahView(persistentListOf(), 0, false),
  /** Newest first. */
  val sessions: ImmutableList<SessionView> = persistentListOf(),
  val uncreditedTodayMinutes: Int = 0,
  val reminder: ReminderView = ReminderView(on = true, minuteOfDay = 20 * 60, allowed = true),
  /** Whether more bookmarks can be turned on. */
  val canAddBookmark: Boolean = true,
)
