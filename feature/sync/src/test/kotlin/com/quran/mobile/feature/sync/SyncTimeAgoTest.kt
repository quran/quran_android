package com.quran.mobile.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncTimeAgoTest {
  private val now = Instant.parse("2026-10-31T12:00:00Z")

  @Test fun `the last minute and small clock skew read just now`() {
    assertEquals("just now", timeAgo(now))
    assertEquals("just now", timeAgo(now - 59.seconds))
    assertEquals("just now", timeAgo(now + 59.minutes))
  }

  @Test fun `older than 30 days or further in the future shows the full date`() {
    assertEquals("Wednesday, September 30, 2026", timeAgo(now - 31.days))
    assertEquals("Saturday, October 31, 2026", timeAgo(now + 2.hours))
  }

  private fun timeAgo(at: Instant) = syncedTimeAgo(at, now, "just now", Locale.US)
}
