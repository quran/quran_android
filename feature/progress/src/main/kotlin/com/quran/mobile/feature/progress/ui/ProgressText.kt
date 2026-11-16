package com.quran.mobile.feature.progress.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.mobile.feature.progress.R
import com.quran.mobile.progress.time.LocalDays
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone

/** Numbers in charts line up: tabular figures. */
internal fun TextStyle.tabular(): TextStyle = merge(TextStyle(fontFeatureSettings = "tnum"))

/** The locale's digits (Arabic-Indic in Arabic). */
@Composable
internal fun rememberNumbers(): (Int) -> String {
  val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
  val format = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
  return remember(format) { { value: Int -> format.format(value) } }
}

/** Noon of a local day: safely inside it whatever the time zone does. */
internal fun noonOf(day: Long, zone: TimeZone = TimeZone.getDefault()): Long =
  LocalDays.startOfDayMs(day, zone) + 12 * 3_600_000L

/** E.g. "Sunday 3 November". */
internal fun Context.dayWithWeekday(day: Long): String = DateUtils.formatDateTime(
  this, noonOf(day), DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
)

/** E.g. "2 December". */
internal fun Context.dayOnly(day: Long): String =
  DateUtils.formatDateTime(this, noonOf(day), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)

/** E.g. "2 – 30 October". */
internal fun Context.dayRange(first: Long, last: Long): String = DateUtils.formatDateRange(
  this, noonOf(first), noonOf(last), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
)

/** E.g. "8:00 pm", in the device's 12 or 24 hour clock. */
internal fun Context.timeOfDay(minuteOfDay: Int): String =
  DateUtils.formatDateTime(this, LocalDays.timeOnDay(0, minuteOfDay, TimeZone.getDefault()), DateUtils.FORMAT_SHOW_TIME)

@Composable
internal fun duration(ms: Long): String {
  val minutes = (ms / 60_000L).toInt()
  return if (minutes >= 60) {
    stringResource(R.string.progress_hours_minutes, minutes / 60, minutes % 60)
  } else {
    stringResource(R.string.progress_minutes_only, minutes)
  }
}

@Composable
internal fun purposeName(purpose: ReadingBookmarkPurpose): String = stringResource(
  when (purpose) {
    ReadingBookmarkPurpose.READING -> R.string.progress_purpose_reading
    ReadingBookmarkPurpose.MEMORIZATION -> R.string.progress_purpose_memorization
    ReadingBookmarkPurpose.REVIEW -> R.string.progress_purpose_review
  }
)

@Composable
internal fun pages(count: Int): String = pluralStringResource(R.plurals.progress_pages, count, count)

@Composable
internal fun minutes(count: Int): String = pluralStringResource(R.plurals.progress_minutes, count, count)

@Composable
internal fun days(count: Int): String = pluralStringResource(R.plurals.progress_days, count, count)

@Composable
internal fun context(): Context = LocalContext.current

/**
 * The panels' color: the app's surface with a touch of its surface variant (Material's container
 * roles aren't part of the app's theme).
 */
@Composable
internal fun panelColor(): Color {
  val scheme = MaterialTheme.colorScheme
  return scheme.surfaceVariant.copy(alpha = 0.38f).compositeOver(scheme.surface)
}
