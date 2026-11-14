package com.quran.mobile.progress.time

import java.util.Calendar
import java.util.TimeZone

/** Local days as epoch days (days since 1970-01-01 in the given time zone), without java.time. */
object LocalDays {
  const val DAY_MS = 86_400_000L

  /** The local day [ms] falls on in [zone]. */
  fun epochDay(ms: Long, zone: TimeZone): Long = (ms + zone.getOffset(ms)).floorDiv(DAY_MS)

  /** When local [day] starts in [zone] (the first moment of it, if midnight is skipped by a clock change). */
  fun startOfDayMs(day: Long, zone: TimeZone): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * DAY_MS }
    val local = Calendar.getInstance(zone).apply {
      clear()
      set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }
    return local.timeInMillis
  }

  /** The time [minuteOfDay] minutes into local [day] in [zone]. */
  fun timeOnDay(day: Long, minuteOfDay: Int, zone: TimeZone): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * DAY_MS }
    val local = Calendar.getInstance(zone).apply {
      clear()
      set(
        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
        minuteOfDay / 60, minuteOfDay % 60, 0
      )
    }
    return local.timeInMillis
  }
}
