package com.quran.mobile.progress.reminder

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.progress.cycle.Cycle
import com.quran.mobile.progress.cycle.PaceMath
import com.quran.mobile.progress.summary.BookmarkProgress
import com.quran.mobile.progress.summary.DayAmount
import com.quran.mobile.progress.time.LocalDays
import org.junit.Test
import java.util.TimeZone

class ReminderDecisionTest {
  private val cairo = TimeZone.getTimeZone("Africa/Cairo")
  private val today = 20_400L
  private val eightPm = 20 * 60
  private fun at(hour: Int, minute: Int = 0) = LocalDays.timeOnDay(today, hour * 60 + minute, cairo)

  /** A teal cycle started [dayN] days ago (day 1 = today), the bookmark on [page], [todayPages] read today. */
  private fun cycle(dayN: Int, page: Int, todayPages: Int = 0, slot: ReadingBookmarkType = ReadingBookmarkType.TEAL) =
    BookmarkProgress(
      slot = slot,
      name = null,
      purpose = ReadingBookmarkPurpose.READING,
      targetDays = 30,
      position = null,
      cycle = Cycle(0L, today - dayN + 1, partial = false),
      cycleStartPage = 1,
      pace = PaceMath.pace(today - dayN + 1, today, page, 1, 604, 30),
      firstDay = today - dayN + 1,
      chartDays = 30,
      days = listOf(DayAmount(today, todayPages, todayPages * 2)),
      cycleReadingMs = 0,
      cyclePages = page - 1,
      lastCycle = null,
      moved = true,
    )

  private fun decide(
    bookmarks: List<BookmarkProgress>,
    now: Long = at(20, 5),
    enabled: Boolean = true,
    last: Long = today - 1,
    readerOpen: Boolean = false,
  ) = ReminderDecision.decide(enabled, now, cairo, eightPm, last, readerOpen, bookmarks)

  @Test
  fun offSaysNothing() {
    assertThat(decide(listOf(cycle(10, 50)), enabled = false)).isEqualTo(Reminder.None)
  }

  @Test
  fun noOpenCycleWithADeadlineSaysNothing() {
    assertThat(decide(listOf(cycle(10, 50).copy(pace = null)))).isEqualTo(Reminder.None)
    assertThat(decide(emptyList())).isEqualTo(Reminder.None)
  }

  @Test
  fun onceADay() {
    assertThat(decide(listOf(cycle(10, 50)), last = today)).isEqualTo(Reminder.None)
  }

  @Test
  fun notBeforeItsTime() {
    assertThat(decide(listOf(cycle(10, 50)), now = at(19, 59))).isEqualTo(Reminder.None)
  }

  @Test
  fun notWhileReading() {
    assertThat(decide(listOf(cycle(10, 50)), readerOpen = true)).isEqualTo(Reminder.None)
  }

  @Test
  fun aheadAndReadTodaySaysNothing() {
    assertThat(decide(listOf(cycle(10, 300, todayPages = 20)))).isEqualTo(Reminder.None)
  }

  @Test
  fun aheadButNotReadToday() {
    val reminder = decide(listOf(cycle(10, 300)))
    assertThat(reminder).isInstanceOf(Reminder.NotReadToday::class.java)
    // 305 pages in 21 days
    assertThat((reminder as Reminder.NotReadToday).perDay).isEqualTo(15)
  }

  @Test
  fun behindSaysByHowMuch() {
    val reminder = decide(listOf(cycle(10, 101, todayPages = 5)))
    assertThat(reminder).isInstanceOf(Reminder.Behind::class.java)
    reminder as Reminder.Behind
    assertThat(reminder.pages).isEqualTo(102)
    assertThat(reminder.finishByDay).isEqualTo(today - 9 + 29)
  }

  @Test
  fun overdueComesFirst() {
    val reminder = decide(listOf(cycle(10, 101), cycle(33, 500, slot = ReadingBookmarkType.GREEN)))
    assertThat(reminder).isInstanceOf(Reminder.Overdue::class.java)
    assertThat((reminder as Reminder.Overdue).remaining).isEqualTo(105)
  }

  @Test
  fun theNextReminderIsTodayAtItsTimeOrTomorrow() {
    assertThat(ReminderDecision.nextReminderAt(at(9), cairo, eightPm)).isEqualTo(at(20))
    assertThat(ReminderDecision.nextReminderAt(at(20), cairo, eightPm))
      .isEqualTo(LocalDays.timeOnDay(today + 1, eightPm, cairo))
  }

  @Test
  fun theNextReminderKeepsItsLocalTimeOverAClockChange() {
    // London moves its clocks forward on 29 March 2026
    val london = TimeZone.getTimeZone("Europe/London")
    val beforeChange = LocalDays.epochDay(1_774_656_000_000L, london) // 2026-03-28
    val now = LocalDays.timeOnDay(beforeChange, 21 * 60, london)
    val next = ReminderDecision.nextReminderAt(now, london, eightPm)
    assertThat(next - now).isEqualTo(22 * 3_600_000L) // 21:00 GMT to 20:00 BST is 22 hours
  }
}
