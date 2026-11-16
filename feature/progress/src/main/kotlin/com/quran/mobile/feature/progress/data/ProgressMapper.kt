package com.quran.mobile.feature.progress.data

import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.feature.progress.ui.BookmarkPlace
import com.quran.mobile.feature.progress.ui.BookmarkView
import com.quran.mobile.feature.progress.ui.DayColumn
import com.quran.mobile.feature.progress.ui.JuzLineState
import com.quran.mobile.feature.progress.ui.JuzPages
import com.quran.mobile.feature.progress.ui.MemorizingView
import com.quran.mobile.feature.progress.ui.ProgressUiState
import com.quran.mobile.feature.progress.ui.ReminderView
import com.quran.mobile.feature.progress.ui.SalahView
import com.quran.mobile.feature.progress.ui.SessionAyahView
import com.quran.mobile.feature.progress.ui.SessionView
import com.quran.mobile.progress.summary.BookmarkProgress
import com.quran.mobile.progress.summary.ProgressCalculator
import com.quran.mobile.progress.summary.SessionDay
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.math.roundToInt

/**
 * Turns a [ProgressSnapshot] into what the screen shows.
 *
 * @param bookmarkName a bookmark's name: the user's, or its colour's.
 * @param suraName a sura's name.
 * @param dayOfMonth a local day's day of the month, which labels a chart of the last 30 days.
 */
class ProgressMapper(
  private val bookmarkName: (ReadingBookmarkType, String?) -> String,
  private val suraName: (Int) -> String,
  private val dayOfMonth: (Long) -> Int,
) {

  fun map(snapshot: ProgressSnapshot, reminder: ReminderView, offered: Int): ProgressUiState {
    val calculator = snapshot.calculator
    val bookmarks = snapshot.bookmarks.map { bookmark(it, calculator, snapshot.today) }
    val heldMost = snapshot.memorizing.juz.filter { it.held > 0 }.maxByOrNull { it.held }?.number
    return ProgressUiState(
      today = snapshot.today,
      bookmarks = bookmarks.toImmutableList(),
      memorizing = MemorizingView(
        focusJuz = bookmarks.firstOrNull { it.purpose == ReadingBookmarkPurpose.MEMORIZATION && it.juz != null }?.juz ?: heldMost,
        juz = snapshot.memorizing.juz.toImmutableList(),
        suras = snapshot.memorizing.suras.mapValues { it.value.toImmutableList() }.toImmutableMap(),
        days = sessionColumns(snapshot.memorizing.days, snapshot.today) { it.accuracy to it.hinted },
        averageAccuracy = snapshot.memorizing.days.filter { it.words > 0 }.takeIf { it.isNotEmpty() }
          ?.let { days -> days.sumOf { it.recited } * 100 / days.sumOf { it.words } },
        hasHistory = snapshot.memorizing.hasData,
      ),
      salah = SalahView(
        days = sessionColumns(snapshot.salah.days, snapshot.today) { it.sessions to it.hinted },
        prayers = snapshot.salah.days.sumOf { it.sessions },
        hasHistory = snapshot.salah.hasData,
      ),
      sessions = snapshot.sessions.asReversed().map { logged ->
        val record = logged.record
        SessionView(
          id = record.id,
          title = passage(record.startSura, record.startAyah, record.endSura, record.endAyah),
          day = logged.day,
          startedAtMs = record.startedAtMs,
          mode = record.mode,
          from = "${suraName(record.startSura)} ${record.startSura}:${record.startAyah}",
          to = "${suraName(record.endSura)} ${record.endSura}:${record.endAyah}",
          minutes = ((record.endedAtMs - record.startedAtMs + 59_999L) / 60_000L).toInt(),
          recited = record.recited,
          hinted = record.hinted,
          missed = record.missed,
          finished = record.finished,
          recordingId = record.recordingId,
          ayahs = record.ayahs.map {
            SessionAyahView(suraName(it.sura), it.sura, it.ayah, it.ayahWords, it.words, it.hinted, it.missed)
          }.toImmutableList(),
        )
      }.toImmutableList(),
      uncreditedTodayMinutes = (snapshot.uncreditedTodayMs / 60_000L).toInt(),
      reminder = reminder,
      canAddBookmark = snapshot.bookmarks.size < offered,
    )
  }

  private fun bookmark(progress: BookmarkProgress, calculator: ProgressCalculator, today: Long): BookmarkView {
    val position = progress.position
    val page = position?.let(calculator::pageOf)
    val pace = progress.pace
    val startPage = progress.cycleStartPage
    val cycleChart = pace != null
    val columns = (0 until progress.chartDays).map { index ->
      val day = progress.firstDay + index
      val amount = progress.days.getOrNull(index)
      DayColumn(
        dayN = index + 1,
        label = if (cycleChart) index + 1 else dayOfMonth(day),
        above = if (day > today) null else amount?.pages ?: 0,
        below = if (day > today) null else amount?.minutes ?: 0,
        isToday = day == today,
      )
    }
    val elapsed = progress.days
    val juzPages = (1..30).map { juz ->
      val pages = calculator.juzPages(juz)
      val read = if (page == null || startPage == null) 0 else (pages.first..pages.last).count { it in startPage until page }
      JuzPages(juz, pages.first, pages.last, read)
    }
    return BookmarkView(
      slot = progress.slot,
      name = bookmarkName(progress.slot, progress.name),
      purpose = progress.purpose,
      targetDays = progress.targetDays,
      place = position?.let { BookmarkPlace(page!!, suraName(it.sura), it.sura, it.ayah) },
      juz = position?.let { calculator.juzOf(it.sura, it.ayah) },
      cycleStartDay = progress.cycle?.takeIf { !it.partial }?.startDay,
      followedSinceDay = progress.cycle?.takeIf { it.partial }?.startDay,
      dayN = pace?.dayN,
      finishByDay = pace?.finishByDay,
      overdue = pace?.overdue == true,
      ahead = pace?.ahead,
      line = JuzLineState(
        position = page?.let(calculator::juzPosition),
        runStart = startPage?.let(calculator::juzPosition),
        pace = pace?.let { calculator.juzPosition((startPage ?: 1) + it.expectedByToday.coerceAtMost(it.totalPages - 1)) },
        current = position?.let { calculator.juzOf(it.sura, it.ayah) },
        juz = juzPages.toImmutableList(),
      ),
      days = columns.toImmutableList(),
      goal = pace?.let { (it.totalPages.toDouble() / it.targetDays).roundToInt() },
      averageMinutes = elapsed.takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.minutes } / days.size },
      todayMinutes = progress.today?.takeIf { it.day == today }?.minutes,
      cycleReadingMs = progress.cycleReadingMs,
      cyclePages = progress.cyclePages,
      lastCycleDays = progress.lastCycle?.days,
      lastCycleStartDay = progress.lastCycle?.startDay,
      lastCycleEndDay = progress.lastCycle?.endDay,
      hasHistory = progress.moved,
    )
  }

  /** E.g. "Al-Ikhlas 1–4", or "Al-Falaq 1 – An-Nas 6" across suras. */
  private fun passage(startSura: Int, startAyah: Int, endSura: Int, endAyah: Int): String = when {
    startSura != endSura -> "${suraName(startSura)} $startAyah – ${suraName(endSura)} $endAyah"
    startAyah == endAyah -> "${suraName(startSura)} $startAyah"
    else -> "${suraName(startSura)} $startAyah–$endAyah"
  }

  private fun sessionColumns(days: List<SessionDay>, today: Long, values: (SessionDay) -> Pair<Int, Int>) =
    days.mapIndexed { index, day ->
      val (above, below) = values(day)
      DayColumn(index + 1, dayOfMonth(day.day), above, below, isToday = day.day == today, empty = day.sessions == 0)
    }.toImmutableList()
}
