package com.quran.mobile.progress.summary

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.progress.Moves
import com.quran.mobile.progress.history.DayMinutes
import com.quran.mobile.progress.history.LoggedSession
import com.quran.mobile.progress.session.AyahResult
import com.quran.mobile.progress.session.SessionRecord
import org.junit.Test
import kotlin.time.Instant

class ProgressCalculatorTest {
  private val quranInfo = QuranInfo(MadaniDataSource())
  private val calculator = ProgressCalculator(quranInfo, "madani")
  private val teal = ReadingBookmarkSlotSetting(ReadingBookmarkType.TEAL, enabled = true, targetDays = 30)

  private fun at(page: Int) = PageReadingBookmark(ReadingBookmarkType.TEAL, page, Instant.fromEpochMilliseconds(0))
  /** [minutes] read on [day], credited by the move at [creditedAt] (uncredited without a slot). */
  private fun minutes(day: Long, minutes: Int, creditedAt: Long, slot: String? = "TEAL") =
    DayMinutes(slot, day, minutes * 60_000L, creditedAt.takeIf { slot != null })

  @Test
  fun aCycleWithADeadlineHasItsPaceAndADayForEachPageAndMinute() {
    val moves = Moves.of {
      day(100) {
        baseline(1, 1, 1)
        move(21, 2, 137)
      }
      day(101) { move(45, 3, 1) }
    }
    val progress = calculator.bookmark(
      at(45), teal, moves,
      listOf(minutes(100, 30, moves[1].atMs), minutes(101, 20, moves[2].atMs), minutes(101, 5, 0, slot = null)),
      today = 101
    )
    assertThat(progress.pace!!.dayN).isEqualTo(2)
    assertThat(progress.pace!!.pagesDone).isEqualTo(44)
    assertThat(progress.firstDay).isEqualTo(100)
    assertThat(progress.chartDays).isEqualTo(30)
    assertThat(progress.days).containsExactly(DayAmount(100, 20, 30), DayAmount(101, 24, 20)).inOrder()
    assertThat(progress.cycleReadingMs).isEqualTo(50 * 60_000L)
    assertThat(progress.cyclePages).isEqualTo(44)
    assertThat(progress.lastCycle).isNull()
    assertThat(progress.moved).isTrue()
  }

  @Test
  fun aWrapEndsTheLastCycleWithItsDatesAndTime() {
    val moves = Moves.of {
      day(10) { baseline(1, 1, 1) }
      day(20) { move(300, 16, 1) }
      day(37) { move(604, 114, 1) }
      day(38) { move(2, 2, 1) }
    }
    val wrap = moves[3].atMs
    val reading = listOf(
      minutes(20, 100, moves[1].atMs),
      minutes(37, 40, moves[2].atMs),
      // the end of the Quran, read on the day it wrapped: credited by the wrap, to the cycle it closes
      minutes(38, 25, wrap),
      // read after the wrap, credited by a later move
      minutes(38, 10, wrap + 3_600_000L),
    )
    val progress = calculator.bookmark(at(2), teal, moves, reading, today = 38)
    assertThat(progress.lastCycle).isEqualTo(ClosedCycle(startDay = 10, endDay = 38, readingMs = 165 * 60_000L))
    assertThat(progress.lastCycle!!.days).isEqualTo(29)
    assertThat(progress.cycle!!.startDay).isEqualTo(38)
    assertThat(progress.pace!!.dayN).isEqualTo(1)
    assertThat(progress.days.single()).isEqualTo(DayAmount(38, 1, 10))
    assertThat(progress.cycleReadingMs).isEqualTo(10 * 60_000L)
  }

  @Test
  fun aCycleEndsWhenItsLastPagesWereReadNotWhenTheNextOneStarts() {
    val moves = Moves.of {
      day(10) { baseline(1, 1, 1) }
      day(37) { move(604, 114, 1) }
      day(41) { move(1, 1, 1) }
    }
    val progress = calculator.bookmark(at(1), teal, moves, listOf(minutes(37, 40, moves[2].atMs)), today = 41)
    assertThat(progress.lastCycle).isEqualTo(ClosedCycle(startDay = 10, endDay = 37, readingMs = 40 * 60_000L))
    assertThat(progress.cycle!!.startDay).isEqualTo(41)
  }

  @Test
  fun withoutADeadlineTheChartIsTheLast30Days() {
    val study = teal.copy(slot = ReadingBookmarkType.ORANGE, targetDays = 0)
    val moves = Moves.of {
      day(100) { baseline(40, 3, 1) }
      day(120) { move(60, 3, 92) }
    }
    val progress = calculator.bookmark(
      PageReadingBookmark(ReadingBookmarkType.ORANGE, 60, Instant.fromEpochMilliseconds(0)), study, moves, emptyList(), today = 125
    )
    assertThat(progress.pace).isNull()
    assertThat(progress.firstDay).isEqualTo(96)
    assertThat(progress.days).hasSize(30)
    assertThat(progress.days.single { it.pages > 0 }).isEqualTo(DayAmount(120, 20, 0))
  }

  @Test
  fun aMemorizationBookmarkHasNoCycles() {
    val hifz = teal.copy(purpose = ReadingBookmarkPurpose.MEMORIZATION)
    val moves = Moves.of {
      day(1) { baseline(600, 109, 1) }
      day(2) { move(1, 1, 1) }
    }
    val progress = calculator.bookmark(at(1), hifz, moves, emptyList(), today = 2)
    assertThat(progress.lastCycle).isNull()
    assertThat(progress.pace).isNull()
  }

  @Test
  fun theJuzLineRunsFromTheStartOfJuz1ToTheEndOfJuz30() {
    assertThat(calculator.juzPosition(1)).isEqualTo(0f)
    assertThat(calculator.juzPosition(quranInfo.getStartingPageForJuz(30))).isEqualTo(29f)
    assertThat(calculator.juzPosition(604)).isLessThan(30f)
    assertThat(calculator.juzOf(2, 142)).isEqualTo(2)
    assertThat(calculator.juzOf(2, 141)).isEqualTo(1)
    assertThat(calculator.juzOf(114, 6)).isEqualTo(30)
  }

  @Test
  fun anAyahIsHeldWhenItsLastMemorizingWentWithoutHelp() {
    fun session(id: String, day: Long, hinted: Int) = LoggedSession(
      day,
      SessionRecord(
        id, "MEMORIZE", day * 86_400_000L, day * 86_400_000L + 60_000, 50_000, 112, 1, 112, 2, 6, 6 - hinted, hinted, 0, 0,
        finished = false, recordingId = null,
        ayahs = listOf(
          AyahResult(112, 1, ayahWords = 4, words = 4, hinted = 0, missed = 0, mistakes = 0),
          AyahResult(112, 2, ayahWords = 2, words = 2, hinted = hinted, missed = 0, mistakes = 0),
        ),
      ),
    )
    val clean = calculator.memorizing(listOf(session("a", 5, hinted = 0)), today = 5)
    assertThat(clean.juz.single { it.number == 30 }.held).isEqualTo(2)
    assertThat(clean.suras.getValue(30).single { it.number == 112 }).isEqualTo(Held(112, 2, 4))

    val later = calculator.memorizing(listOf(session("a", 5, hinted = 0), session("b", 6, hinted = 1)), today = 6)
    assertThat(later.juz.single { it.number == 30 }.held).isEqualTo(1)
    assertThat(later.days.last().accuracy).isEqualTo(83)
    assertThat(later.juz.sumOf { it.ayat }).isEqualTo(6236)
  }
}
