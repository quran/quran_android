package com.quran.mobile.progress.summary

import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.progress.cycle.Cycle
import com.quran.mobile.progress.cycle.Pace
import com.quran.mobile.progress.log.BookmarkPosition

/** One day of a bookmark's chart: pages it moved forward, and minutes of reading credited to it. */
data class DayAmount(val day: Long, val pages: Int, val minutes: Int)

/** A finished cycle: from [startDay] to [endDay], local days, both included, with [readingMs] read. */
data class ClosedCycle(val startDay: Long, val endDay: Long, val readingMs: Long) {
  val days: Int get() = (endDay - startDay + 1).toInt()
}

/**
 * Where one reading bookmark stands.
 *
 * @param position where it is now (in the page type shown), or null when it isn't placed.
 * @param cycle the open cycle, once Progress has seen the bookmark.
 * @param cycleStartPage where the open cycle started: the first page, or where Progress first saw
 * the bookmark when it started before.
 * @param pace against [targetDays], for a reading bookmark whose open cycle's start is known.
 * @param firstDay day 1 of the chart: the cycle's start, or 29 days before today when the chart is
 * just the last 30 days (no deadline, or a cycle that started before Progress kept track).
 * @param chartDays the days the chart has room for, from [firstDay] (some may be still to come).
 * @param days the chart's days up to today, oldest first.
 * @param cycleReadingMs reading credited to it since the open cycle started.
 * @param cyclePages pages it moved forward since then.
 * @param lastCycle the last cycle it finished.
 * @param moved whether it has moved since Progress kept track (else there is nothing to show yet).
 */
data class BookmarkProgress(
  val slot: ReadingBookmarkType,
  val name: String?,
  val purpose: ReadingBookmarkPurpose,
  val targetDays: Int,
  val position: BookmarkPosition?,
  val cycle: Cycle?,
  val cycleStartPage: Int?,
  val pace: Pace?,
  val firstDay: Long,
  val chartDays: Int,
  val days: List<DayAmount>,
  val cycleReadingMs: Long,
  val cyclePages: Int,
  val lastCycle: ClosedCycle?,
  val moved: Boolean,
) {
  val today: DayAmount? get() = days.lastOrNull()
}

/** Ayat held in one juz or sura: the last time each was memorized, all its words, no hint and none missed. */
data class Held(val number: Int, val held: Int, val ayat: Int)

/** One day of sessions of one mode. */
data class SessionDay(
  val day: Long,
  val sessions: Int,
  val words: Int,
  val recited: Int,
  val hinted: Int,
  val missed: Int,
  val mistakes: Int,
) {
  /** Words recited without help, out of every word gone through, in percent. */
  val accuracy: Int get() = if (words == 0) 0 else (recited * 100 + words / 2) / words
}

data class MemorizingProgress(
  val juz: List<Held>,
  /** Per juz (1 to 30), its suras (a sura split between two juz is in both, with its ayat there). */
  val suras: Map<Int, List<Held>>,
  /** Memorize sessions, the last 30 days (none for a day without one). */
  val days: List<SessionDay>,
  val firstDay: Long,
  val hasData: Boolean,
)

data class SalahProgress(val days: List<SessionDay>, val firstDay: Long, val hasData: Boolean)
