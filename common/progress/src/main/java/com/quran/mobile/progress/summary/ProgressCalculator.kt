package com.quran.mobile.progress.summary

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.mobile.progress.cycle.Cycle
import com.quran.mobile.progress.cycle.CycleDetector
import com.quran.mobile.progress.cycle.DailyProgress
import com.quran.mobile.progress.cycle.PaceMath
import com.quran.mobile.progress.history.DayMinutes
import com.quran.mobile.progress.history.LoggedSession
import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove
import com.quran.mobile.progress.log.position

/**
 * Works out what Progress shows from what it kept, in the page type shown ([quranInfo] is that
 * page type's, [pageType] its name).
 */
class ProgressCalculator(private val quranInfo: QuranInfo, private val pageType: String) {
  private val firstPage = 1 + quranInfo.skip
  private val lastPage = quranInfo.numberOfPages
  private val totalPages = quranInfo.numberOfPagesConsideringSkipped

  // where each juz starts, by sura and ayah (the same in every Hafs page type)
  private val juzStarts: List<SuraAyah> = (0 until 30).map { juz ->
    quranInfo.getQuarterByIndex(juz * 8).let { SuraAyah(it.sura, it.ayah) }
  }

  fun juzOf(sura: Int, ayah: Int): Int = juzStarts.indexOfLast { it <= SuraAyah(sura, ayah) } + 1

  /** A position's page in the page type shown. */
  fun pageOf(position: BookmarkPosition): Int =
    if (position.pageType == pageType) position.page else quranInfo.getPageFromSuraAyah(position.sura, position.ayah)

  /** Where [page] is along the 30 juz, from 0 (the start of juz 1) to 30 (the end of juz 30). */
  fun juzPosition(page: Int): Float {
    val juz = quranInfo.getJuzFromPage(page)
    val start = quranInfo.getStartingPageForJuz(juz)
    val end = if (juz == 30) lastPage + 1 else quranInfo.getStartingPageForJuz(juz + 1)
    val span = (end - start).coerceAtLeast(1)
    return (juz - 1) + ((page - start).toFloat() / span).coerceIn(0f, 1f)
  }

  /** The pages of [juz] in the page type shown. */
  fun juzPages(juz: Int): IntRange {
    val start = quranInfo.getStartingPageForJuz(juz)
    val end = if (juz == 30) lastPage else quranInfo.getStartingPageForJuz(juz + 1) - 1
    return start..end.coerceAtLeast(start)
  }

  fun bookmark(
    bookmark: ReadingBookmark,
    setting: ReadingBookmarkSlotSetting,
    moves: List<RecordedMove>,
    minutes: List<DayMinutes>,
    today: Long,
  ): BookmarkProgress {
    val slot = bookmark.slot
    val position = bookmark.position(quranInfo, pageType)
    val reading = setting.purpose == ReadingBookmarkPurpose.READING
    // a bookmark for something else than reading through has no cycles: one span since it was first seen
    val cycles = if (reading) {
      CycleDetector.cycles(moves)
    } else {
      moves.firstOrNull()?.let { listOf(Cycle(it.atMs, it.day, partial = true)) }.orEmpty()
    }
    val open = cycles.lastOrNull()?.takeIf { it.isOpen }
    // the last cycle finished whose start is known
    val closed = cycles.lastOrNull { !it.isOpen && !it.partial }
    // reading credited to this bookmark: a move credits what was read before it, so a cycle has
    // what its own moves credited (the wrap's move credits the end of the cycle it closes)
    val credited = minutes.filter { it.slot == slot.name }
    fun creditedIn(fromMs: Long, toMs: Long?) =
      credited.filter { (it.creditedAt ?: 0L) > fromMs && (toMs == null || (it.creditedAt ?: 0L) <= toMs) }

    val pace = if (reading && open != null && !open.partial && setting.targetDays > 0 && position != null) {
      PaceMath.pace(open.startDay, today, pageOf(position), firstPage, totalPages, setting.targetDays)
    } else {
      null
    }
    val firstDay: Long
    val chartDays: Int
    if (pace != null && open != null) {
      firstDay = open.startDay
      chartDays = maxOf(setting.targetDays, pace.dayN).coerceAtMost(MAX_CHART_DAYS)
    } else {
      firstDay = today - (RECENT_DAYS - 1)
      chartDays = RECENT_DAYS
    }
    val pagesByDay = if (open != null) DailyProgress.pagesPerDay(open, moves, ::pageOf, firstPage, lastPage) else emptyMap()
    val cycleCredit = open?.let { creditedIn(it.startMs, null) }.orEmpty()
    // a cycle's chart shows its own reading; the last 30 days show all of it
    val minutesByDay = (if (pace != null) cycleCredit else credited).groupBy({ it.day }, { it.ms }).mapValues { it.value.sum() }
    val lastShown = minOf(today, firstDay + chartDays - 1)
    val days = (firstDay..lastShown).map { day ->
      DayAmount(day, pagesByDay[day] ?: 0, ((minutesByDay[day] ?: 0L) / 60_000L).toInt())
    }
    val cycleReadingMs = cycleCredit.sumOf { it.ms }
    val cyclePages = pace?.pagesDone ?: pagesByDay.values.sum()
    val lastCycle = closed?.let { cycle ->
      val reading = creditedIn(cycle.startMs, cycle.endMs)
      // it ended when its last pages were read (the bookmark may go back to the start days later)
      val lastMove = moves.lastOrNull { it.kind == MoveKind.MOVE && it.atMs > cycle.startMs && it.atMs < (cycle.endMs ?: 0L) }
      val endDay = maxOf(lastMove?.day ?: cycle.startDay, reading.maxOfOrNull { it.day } ?: cycle.startDay)
        .takeIf { cycle.wrapped } ?: (cycle.endDay ?: cycle.startDay)
      ClosedCycle(cycle.startDay, endDay, reading.sumOf { it.ms })
    }
    val cycleStartPage = open?.let { cycle ->
      if (!cycle.partial) firstPage else moves.firstOrNull { it.atMs >= cycle.startMs && it.position != null }?.position?.let(::pageOf)
    }
    return BookmarkProgress(
      slot = slot,
      name = bookmark.name,
      purpose = setting.purpose,
      targetDays = setting.targetDays,
      position = position,
      cycle = open,
      cycleStartPage = cycleStartPage,
      pace = pace,
      firstDay = firstDay,
      chartDays = chartDays,
      days = days,
      cycleReadingMs = cycleReadingMs,
      cyclePages = cyclePages,
      lastCycle = lastCycle,
      moved = moves.any { it.kind == MoveKind.MOVE },
    )
  }

  fun memorizing(sessions: List<LoggedSession>, today: Long): MemorizingProgress {
    val memorize = sessions.filter { it.record.mode == MEMORIZE }
    // the last time each ayah was gone through, in order
    val last = HashMap<SuraAyah, Boolean>()
    memorize.sortedBy { it.record.startedAtMs }.forEach { session ->
      session.record.ayahs.forEach { ayah ->
        if (ayah.words >= ayah.ayahWords) {
          last[SuraAyah(ayah.sura, ayah.ayah)] = ayah.hinted == 0 && ayah.missed == 0
        }
      }
    }
    val juzAyat = IntArray(31)
    val juzHeld = IntArray(31)
    val suraAyat = HashMap<Pair<Int, Int>, Int>()
    val suraHeld = HashMap<Pair<Int, Int>, Int>()
    for (sura in 1..114) {
      for (ayah in 1..quranInfo.getNumberOfAyahs(sura)) {
        val juz = juzOf(sura, ayah)
        val key = juz to sura
        juzAyat[juz]++
        suraAyat[key] = (suraAyat[key] ?: 0) + 1
        if (last[SuraAyah(sura, ayah)] == true) {
          juzHeld[juz]++
          suraHeld[key] = (suraHeld[key] ?: 0) + 1
        }
      }
    }
    val firstDay = today - (RECENT_DAYS - 1)
    return MemorizingProgress(
      juz = (1..30).map { Held(it, juzHeld[it], juzAyat[it]) },
      suras = suraAyat.keys.groupBy({ it.first }, { key -> Held(key.second, suraHeld[key] ?: 0, suraAyat.getValue(key)) })
        .mapValues { (_, suras) -> suras.sortedBy { it.number } },
      days = sessionDays(memorize, firstDay, today),
      firstDay = firstDay,
      hasData = memorize.isNotEmpty(),
    )
  }

  fun salah(sessions: List<LoggedSession>, today: Long): SalahProgress {
    val salah = sessions.filter { it.record.mode == SALAH }
    val firstDay = today - (RECENT_DAYS - 1)
    return SalahProgress(sessionDays(salah, firstDay, today), firstDay, salah.isNotEmpty())
  }

  private fun sessionDays(sessions: List<LoggedSession>, firstDay: Long, today: Long): List<SessionDay> {
    val byDay = sessions.filter { it.day in firstDay..today }.groupBy { it.day }
    return (firstDay..today).map { day ->
      val ofDay = byDay[day].orEmpty().map { it.record }
      SessionDay(
        day = day,
        sessions = ofDay.size,
        words = ofDay.sumOf { it.words },
        recited = ofDay.sumOf { it.recited },
        hinted = ofDay.sumOf { it.hinted },
        missed = ofDay.sumOf { it.missed },
        mistakes = ofDay.sumOf { it.mistakes },
      )
    }
  }

  companion object {
    const val RECENT_DAYS = 30
    const val MAX_CHART_DAYS = 90
    const val MEMORIZE = "MEMORIZE"
    const val SALAH = "SALAH"
  }
}
