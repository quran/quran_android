package com.quran.mobile.feature.progress

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.WidthClass
import com.quran.labs.androidquran.common.ui.core.widthClassOf
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.feature.progress.data.ProgressMapper
import com.quran.mobile.feature.progress.data.ProgressSnapshot
import com.quran.mobile.feature.progress.ui.ReminderView
import com.quran.mobile.progress.history.DayMinutes
import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove
import com.quran.mobile.progress.summary.ProgressCalculator
import org.junit.Test
import kotlin.time.Instant
import androidx.compose.ui.unit.dp

class ProgressMapperTest {
  private val calculator = ProgressCalculator(QuranInfo(MadaniDataSource()), "madani")
  private val mapper = ProgressMapper(
    bookmarkName = { slot, name -> name ?: slot.name },
    suraName = { "S$it" },
    dayOfMonth = { (it % 30).toInt() + 1 },
  )
  private val today = 100L
  private val teal = ReadingBookmarkSlotSetting(ReadingBookmarkType.TEAL, enabled = true, targetDays = 30)

  private fun snapshot(vararg bookmarks: com.quran.mobile.progress.summary.BookmarkProgress) = ProgressSnapshot(
    today = today,
    calculator = calculator,
    bookmarks = bookmarks.toList(),
    memorizing = calculator.memorizing(emptyList(), today),
    salah = calculator.salah(emptyList(), today),
    sessions = emptyList(),
    uncreditedTodayMs = 0,
  )

  private fun map(vararg bookmarks: com.quran.mobile.progress.summary.BookmarkProgress) =
    mapper.map(snapshot(*bookmarks), ReminderView(on = true, minuteOfDay = 1200, allowed = true), offered = 8)

  @Test
  fun aCycleChartHasEveryDayOfTheCycleAndTheRestStillToCome() {
    val moves = listOf(
      RecordedMove(0, 98, MoveKind.BASELINE, BookmarkPosition(1, "madani", 1, 1)),
      RecordedMove(1, 99, MoveKind.MOVE, BookmarkPosition(21, "madani", 2, 137)),
      RecordedMove(2, 100, MoveKind.MOVE, BookmarkPosition(41, "madani", 2, 253)),
    )
    val progress = calculator.bookmark(
      PageReadingBookmark(ReadingBookmarkType.TEAL, 41, Instant.fromEpochMilliseconds(0)), teal, moves,
      listOf(DayMinutes("TEAL", 100, 30 * 60_000L, 2)), today,
    )
    val view = map(progress).bookmarks.single()
    assertThat(view.days).hasSize(30)
    assertThat(view.days.take(3).map { it.above }).containsExactly(0, 20, 20).inOrder()
    assertThat(view.days[2].below).isEqualTo(30)
    assertThat(view.days[2].isToday).isTrue()
    assertThat(view.days.drop(3).all { it.above == null && it.below == null }).isTrue()
    // the labels are the cycle's days
    assertThat(view.days.map { it.label }).isEqualTo((1..30).toList())
    assertThat(view.dayN).isEqualTo(3)
    assertThat(view.goal).isEqualTo(20)
    assertThat(view.juz).isEqualTo(2)
    // juz 1 is read; juz 2 has its first 19 pages read
    assertThat(view.line.juz[0].read).isEqualTo(view.line.juz[0].pages)
    assertThat(view.line.juz[1].read).isEqualTo(41 - view.line.juz[1].firstPage)
    assertThat(view.line.position).isGreaterThan(1f)
    assertThat(view.hasHistory).isTrue()
  }

  @Test
  fun anUnplacedBookmarkHasNoPlaceAndNoHistory() {
    val progress = calculator.bookmark(
      EmptyReadingBookmark(ReadingBookmarkType.GREEN, Instant.fromEpochMilliseconds(0), "Friday"),
      teal.copy(slot = ReadingBookmarkType.GREEN, targetDays = 0), emptyList(), emptyList(), today,
    )
    val state = map(progress)
    val view = state.bookmarks.single()
    assertThat(view.name).isEqualTo("Friday")
    assertThat(view.placed).isFalse()
    assertThat(view.hasHistory).isFalse()
    assertThat(state.canAddBookmark).isTrue()
  }

  @Test
  fun aDayWithoutSessionsIsEmpty() {
    val state = map()
    assertThat(state.salah.days).hasSize(30)
    assertThat(state.salah.days.all { it.empty }).isTrue()
    assertThat(state.memorizing.hasHistory).isFalse()
  }

  @Test
  fun widthClassesFollowMaterial() {
    assertThat(widthClassOf(411.dp)).isEqualTo(WidthClass.Compact)
    assertThat(widthClassOf(700.dp)).isEqualTo(WidthClass.Medium)
    assertThat(widthClassOf(920.dp)).isEqualTo(WidthClass.Expanded)
    assertThat(widthClassOf(1400.dp)).isEqualTo(WidthClass.Expanded)
  }
}
