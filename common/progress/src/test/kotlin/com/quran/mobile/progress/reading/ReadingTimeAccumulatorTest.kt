package com.quran.mobile.progress.reading

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.TimeZone

class ReadingTimeAccumulatorTest {
  private val utc = TimeZone.getTimeZone("UTC")
  private val accumulator = ReadingTimeAccumulator(zone = { utc })
  private val day = 20_000L
  private val start = day * 86_400_000L + 10 * 3_600_000L
  private fun at(seconds: Long) = start + seconds * 1000

  @Test
  fun countsTheTimeAPageIsOpen() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.onPages(at(90), setOf(6))
    accumulator.onPause(at(150))
    assertThat(accumulator.drain()).containsExactly(
      PageTime(day, 5, 90_000, read = true),
      PageTime(day, 6, 60_000, read = true),
    )
  }

  @Test
  fun stopsCountingThreeMinutesAfterTheLastPageTurnOrTouch() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.onInteraction(at(60))
    accumulator.tick(at(600))
    accumulator.onPause(at(900))
    assertThat(accumulator.drain().single().ms).isEqualTo(240_000)
  }

  @Test
  fun aPageSeenOnlyBrieflyIsNotRead() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.onPages(at(5), setOf(6))
    accumulator.onPause(at(65))
    val times = accumulator.drain().associateBy { it.page }
    assertThat(times.getValue(5).read).isFalse()
    assertThat(times.getValue(6).read).isTrue()
  }

  @Test
  fun twoPagesSideBySideShareTheTimeAndAreBothRead() {
    accumulator.onResume(at(0), setOf(4, 5))
    accumulator.onPause(at(60))
    assertThat(accumulator.drain()).containsExactly(
      PageTime(day, 4, 30_000, read = true),
      PageTime(day, 5, 30_000, read = true),
    )
  }

  @Test
  fun timeWhilePausedIsNotCounted() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.onPause(at(30))
    accumulator.tick(at(100))
    accumulator.onResume(at(200), setOf(5))
    accumulator.onPause(at(230))
    assertThat(accumulator.drain().single().ms).isEqualTo(60_000)
  }

  @Test
  fun aClockGoingBackIsIgnored() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.tick(at(-3_600))
    accumulator.onPause(at(-3_570))
    assertThat(accumulator.drain().single().ms).isEqualTo(30_000)
  }

  @Test
  fun aClockJumpingAheadCountsNoMoreThanTheIdleLimit() {
    accumulator.onResume(at(0), setOf(5))
    accumulator.tick(at(86_400))
    assertThat(accumulator.drain().single().ms).isEqualTo(ReadingTimeAccumulator.IDLE_MS)
  }

  @Test
  fun timeIsSplitAtMidnight() {
    val beforeMidnight = (day + 1) * 86_400_000L - 60_000L
    accumulator.onResume(beforeMidnight, setOf(5))
    accumulator.onPause(beforeMidnight + 90_000L)
    assertThat(accumulator.drain()).containsExactly(
      PageTime(day, 5, 60_000, read = true),
      PageTime(day + 1, 5, 30_000, read = true),
    )
  }

  @Test
  fun midnightIsLocal() {
    val cairo = TimeZone.getTimeZone("Africa/Cairo")
    val local = ReadingTimeAccumulator(zone = { cairo })
    // 22:30 UTC is after midnight in Cairo (UTC+2)
    val t = day * 86_400_000L + 22 * 3_600_000L + 30 * 60_000L
    local.onResume(t, setOf(5))
    local.onPause(t + 60_000L)
    assertThat(local.drain().single().day).isEqualTo(day + 1)
  }
}
