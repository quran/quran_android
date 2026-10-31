package com.quran.mobile.feature.sync

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
internal fun syncedTimeAgo(at: Instant, now: Instant): String {
  val justNow = stringResource(R.string.quran_sync_status_just_now)
  val locale = LocalConfiguration.current.locales[0]
  return remember(at, now, justNow, locale) { syncedTimeAgo(at, now, justNow, locale) }
}

internal fun syncedTimeAgo(at: Instant, now: Instant, justNow: String, locale: Locale): String {
  val elapsed = now - at
  return when {
    // a clock a little ahead of the server's can put a sync just after now
    elapsed < 1.minutes && elapsed > -1.hours -> justNow
    elapsed > 30.days || elapsed <= -1.hours ->
      DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date(at.toEpochMilliseconds()))
    else -> relativeTimeAgo(elapsed, locale)
  }
}

private fun relativeTimeAgo(elapsed: Duration, locale: Locale): String {
  val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
  return when {
    elapsed < 1.hours ->
      formatter.format(elapsed.inWholeMinutes.toDouble(), Direction.LAST, RelativeUnit.MINUTES)
    elapsed < 1.days ->
      formatter.format(elapsed.inWholeHours.toDouble(), Direction.LAST, RelativeUnit.HOURS)
    elapsed < 2.days -> formatter.format(Direction.LAST, AbsoluteUnit.DAY)
    elapsed < 7.days ->
      formatter.format(elapsed.inWholeDays.toDouble(), Direction.LAST, RelativeUnit.DAYS)
    elapsed < 14.days -> formatter.format(Direction.LAST, AbsoluteUnit.WEEK)
    else -> formatter.format((elapsed.inWholeDays / 7).toDouble(), Direction.LAST, RelativeUnit.WEEKS)
  }
}
