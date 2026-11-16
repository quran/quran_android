package com.quran.mobile.feature.progress.ui

import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.feature.progress.data.ProgressMapper
import com.quran.mobile.feature.progress.data.ProgressSnapshot
import com.quran.mobile.progress.history.DayMinutes
import com.quran.mobile.progress.history.LoggedSession
import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove
import com.quran.mobile.progress.session.AyahResult
import com.quran.mobile.progress.session.SessionRecord
import com.quran.mobile.progress.summary.ProgressCalculator
import com.quran.mobile.progress.time.LocalDays
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Progress as the renders and the device screenshots show it: made-up data in UTC, a khatma started
 * on Tuesday 3 November 2026 and seen on Saturday 14 November, a memorization bookmark, one with no
 * deadline, one not placed, and twelve days of Memorize and Salah sessions.
 */
object SampleStates {
  private val zone = TimeZone.getTimeZone("UTC")
  private val quranInfo = QuranInfo(MadaniDataSource())
  private val calculator = ProgressCalculator(quranInfo, "madani")
  private val today = LocalDays.epochDay(1_794_657_600_000L, zone)
  private val started = today - 11

  /** The selections the screenshots go through: a name for each, and what's chosen. */
  val screens = listOf(
    "bookmarks" to ProgressSelection(ProgressSection.BOOKMARKS),
    "bookmark" to ProgressSelection(ProgressSection.BOOKMARKS, bookmark = ReadingBookmarkType.TEAL),
    "bookmark_new" to ProgressSelection(ProgressSection.BOOKMARKS, bookmark = ReadingBookmarkType.RED),
    "memorizing" to ProgressSelection(ProgressSection.MEMORIZING),
    "salah" to ProgressSelection(ProgressSection.SALAH),
    "sessions" to ProgressSelection(ProgressSection.SESSIONS, session = "s1"),
  )

  fun state(arabic: Boolean): ProgressUiState {
    val suras = if (arabic) mapOf(11 to "هود", 15 to "الحجر", 112 to "الإخلاص", 113 to "الفلق", 114 to "الناس", 67 to "الملك", 2 to "البقرة")
    else mapOf(11 to "Hud", 15 to "Al-Hijr", 112 to "Al-Ikhlas", 113 to "Al-Falaq", 114 to "An-Nas", 67 to "Al-Mulk", 2 to "Al-Baqara")
    val names = if (arabic) mapOf(ReadingBookmarkType.TEAL to "الختمة", ReadingBookmarkType.ORANGE to "الحفظ", ReadingBookmarkType.RED to "الدراسة")
    else mapOf(ReadingBookmarkType.TEAL to "Khatma", ReadingBookmarkType.ORANGE to "Hifz", ReadingBookmarkType.RED to "Study")
    val mapper = ProgressMapper(
      bookmarkName = { slot, name -> name ?: names[slot] ?: slot.name.lowercase().replaceFirstChar { it.uppercase() } },
      suraName = { suras[it] ?: "Sura $it" },
      dayOfMonth = { day -> ((day - LocalDays.epochDay(1_793_491_200_000L, zone)) + 1).toInt().let { if (it < 1) it + 31 else it } },
    )
    val pages = listOf(16, 22, 19, 26, 10, 0, 24, 21, 27, 15, 25, 24)
    val minutes = listOf(34, 49, 38, 55, 22, 3, 52, 42, 59, 31, 54, 58)
    val (tealMoves, tealPage, tealMinutes) = reading("TEAL", pages, minutes)
    // a cycle finished before: 2 to 30 October
    val lastStart = started - 32
    val earlier = listOf(
      RecordedMove(LocalDays.timeOnDay(lastStart, 7 * 60, zone), lastStart, MoveKind.BASELINE, BookmarkPosition(1, "madani", 1, 1)),
      RecordedMove(LocalDays.timeOnDay(lastStart + 27, 21 * 60, zone), lastStart + 27, MoveKind.MOVE, BookmarkPosition(604, "madani", 114, 1)),
    )
    val earlierMinutes = listOf(DayMinutes("TEAL", lastStart + 10, 27_600_000L, earlier[1].atMs - 1))
    val teal = calculator.bookmark(
      PageReadingBookmark(ReadingBookmarkType.TEAL, tealPage, Instant.fromEpochMilliseconds(0), names[ReadingBookmarkType.TEAL]),
      ReadingBookmarkSlotSetting(ReadingBookmarkType.TEAL, true, ReadingBookmarkPurpose.READING, 30),
      earlier + tealMoves.drop(0).let { moves ->
        // the wrap from the end back to Al-Fatiha starts this cycle
        listOf(moves.first().copy(kind = MoveKind.MOVE)) + moves.drop(1)
      },
      earlierMinutes + tealMinutes,
      today,
    )
    val hifz = calculator.bookmark(
      PageReadingBookmark(ReadingBookmarkType.ORANGE, 582, Instant.fromEpochMilliseconds(0), names[ReadingBookmarkType.ORANGE]),
      ReadingBookmarkSlotSetting(ReadingBookmarkType.ORANGE, true, ReadingBookmarkPurpose.MEMORIZATION, 0),
      listOf(RecordedMove(LocalDays.timeOnDay(today - 20, 9 * 60, zone), today - 20, MoveKind.BASELINE, BookmarkPosition(562, "madani", 67, 1)),
        RecordedMove(LocalDays.timeOnDay(today - 2, 9 * 60, zone), today - 2, MoveKind.MOVE, BookmarkPosition(582, "madani", 78, 1))),
      emptyList(),
      today,
    )
    val study = calculator.bookmark(
      PageReadingBookmark(ReadingBookmarkType.RED, 42, Instant.fromEpochMilliseconds(0), names[ReadingBookmarkType.RED]),
      ReadingBookmarkSlotSetting(ReadingBookmarkType.RED, true, ReadingBookmarkPurpose.READING, 0),
      listOf(RecordedMove(LocalDays.timeOnDay(today, 9 * 60, zone), today, MoveKind.BASELINE, BookmarkPosition(42, "madani", 2, 253))),
      emptyList(),
      today,
    )
    val kahf = calculator.bookmark(
      EmptyReadingBookmark(ReadingBookmarkType.GREEN, Instant.fromEpochMilliseconds(0), if (arabic) "كهف الجمعة" else "Friday Al-Kahf"),
      ReadingBookmarkSlotSetting(ReadingBookmarkType.GREEN, true, ReadingBookmarkPurpose.READING, 0),
      emptyList(),
      emptyList(),
      today,
    )
    val sessions = loggedSessions()
    val snapshot = ProgressSnapshot(
      today = today,
      calculator = calculator,
      bookmarks = listOf(teal, hifz, study, kahf),
      memorizing = calculator.memorizing(sessions, today),
      salah = calculator.salah(sessions, today),
      sessions = sessions,
      uncreditedTodayMs = 0,
    )
    return mapper.map(snapshot, ReminderView(on = true, minuteOfDay = 20 * 60, allowed = true), offered = 8)
  }

  /** A cycle of [slot] from Al-Fatiha on [started], moving on by [pages] a day with [minutes] read. */
  private fun reading(slot: String, pages: List<Int>, minutes: List<Int>): Triple<List<RecordedMove>, Int, List<DayMinutes>> {
    val moves = mutableListOf(
      RecordedMove(LocalDays.timeOnDay(started, 7 * 60, zone), started, MoveKind.BASELINE, BookmarkPosition(1, "madani", 1, 1))
    )
    val credited = mutableListOf<DayMinutes>()
    var page = 1
    pages.forEachIndexed { index, count ->
      val day = started + index
      val at = LocalDays.timeOnDay(day, 21 * 60, zone)
      if (count > 0) {
        page += count
        val bounds = quranInfo.getPageBounds(page)
        moves += RecordedMove(at, day, MoveKind.MOVE, BookmarkPosition(page, "madani", bounds[0], bounds[1]))
      }
      credited += DayMinutes(slot, day, minutes[index] * 60_000L, at)
    }
    return Triple(moves, page, credited)
  }

  private fun loggedSessions(): List<LoggedSession> {
    val result = mutableListOf<LoggedSession>()
    var index = 0
    fun add(day: Long, mode: String, hinted: Int, sura: Int = 112) {
      val ayahs = (1..4).map { AyahResult(sura, it, ayahWords = 4, words = 4, hinted = if (it == 2) hinted else 0, missed = 0, mistakes = 0) }
      val start = LocalDays.timeOnDay(day, 6 * 60 + index * 7, zone)
      result += LoggedSession(
        day,
        SessionRecord(
          "s${++index}", mode, start, start + 9 * 60_000L, 500_000, sura, 1, sura, 4, 16, 16 - hinted, hinted, 0, 0,
          finished = true, recordingId = if (index == 1) "r1" else null, ayahs = ayahs,
        ),
      )
    }
    listOf(9, 6, 11, 0, 5, 3, 6, 0, 2, 4, 3, 2).forEachIndexed { i, hints ->
      val day = today - 11 + i
      if (hints > 0 || i % 4 != 3) add(day, "MEMORIZE", hints, sura = 112 + i % 3)
      repeat(if (i % 5 == 4) 3 else 5) { add(day, "SALAH", if (it == 0) i % 3 else 0, sura = 1) }
    }
    return result.sortedBy { it.record.startedAtMs }
  }
}
