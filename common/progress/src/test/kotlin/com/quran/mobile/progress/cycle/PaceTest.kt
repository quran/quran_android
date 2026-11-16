package com.quran.mobile.progress.cycle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceTest {

  @Test
  fun dayTwelveOfThirtyAtPage262IsAheadOfThePace() {
    val pace = PaceMath.pace(startDay = 100, today = 111, page = 262, firstPage = 1, totalPages = 604, targetDays = 30)
    assertThat(pace.dayN).isEqualTo(12)
    assertThat(pace.pagesDone).isEqualTo(261)
    // 604 × 12 / 30 = 241.6
    assertThat(pace.expectedByToday).isEqualTo(242)
    assertThat(pace.ahead).isEqualTo(19)
    // 343 pages in 19 days, today included
    assertThat(pace.perDayNeeded).isEqualTo(19)
    assertThat(pace.finishByDay).isEqualTo(129)
    // 261 pages in 12 days: 343 more take 16 days
    assertThat(pace.projectedFinishDay).isEqualTo(111 + 16)
    assertThat(pace.overdue).isFalse()
  }

  @Test
  fun behindThePace() {
    val pace = PaceMath.pace(startDay = 0, today = 9, page = 101, firstPage = 1, totalPages = 604, targetDays = 30)
    assertThat(pace.ahead).isEqualTo(100 - 202)
  }

  @Test
  fun nothingReadYetHasNoProjectedFinish() {
    val pace = PaceMath.pace(startDay = 0, today = 0, page = 1, firstPage = 1, totalPages = 604, targetDays = 30)
    assertThat(pace.dayN).isEqualTo(1)
    assertThat(pace.projectedFinishDay).isNull()
    assertThat(pace.perDayNeeded).isEqualTo(21)
  }

  @Test
  fun pastTheDeadlineIsOverdue() {
    val pace = PaceMath.pace(startDay = 0, today = 31, page = 500, firstPage = 1, totalPages = 604, targetDays = 30)
    assertThat(pace.overdue).isTrue()
    assertThat(pace.expectedByToday).isEqualTo(604)
    // the rest is due now
    assertThat(pace.perDayNeeded).isEqualTo(105)
  }

  @Test
  fun skippedPagesAreNotCounted() {
    // a page type whose first page is a cover: 547 pages from page 2
    val pace = PaceMath.pace(startDay = 0, today = 0, page = 2, firstPage = 2, totalPages = 547, targetDays = 30)
    assertThat(pace.pagesDone).isEqualTo(0)
    assertThat(pace.expectedByToday).isEqualTo(19)
  }

  @Test
  fun theLastPageReadIsNotTheEndUntilItWraps() {
    val pace = PaceMath.pace(startDay = 0, today = 20, page = 604, firstPage = 1, totalPages = 604, targetDays = 30)
    assertThat(pace.remaining).isEqualTo(1)
  }
}
