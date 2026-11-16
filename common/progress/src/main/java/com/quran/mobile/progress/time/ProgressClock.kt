package com.quran.mobile.progress.time

import com.quran.data.di.AppScope
import com.quran.mobile.progress.ProgressPreferences
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.TimeZone

/** The time progress is kept in. Debuggable builds may move it by whole days to try out the days ahead. */
interface ProgressClock {
  fun nowMs(): Long
  fun zone(): TimeZone

  /** Whether the clock was moved, so the time things really happened doesn't apply. */
  fun isShifted(): Boolean = false

  fun today(): Long = LocalDays.epochDay(nowMs(), zone())
  fun dayOf(ms: Long): Long = LocalDays.epochDay(ms, zone())
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SystemProgressClock @Inject constructor(private val preferences: ProgressPreferences) : ProgressClock {
  override fun nowMs(): Long = System.currentTimeMillis() + preferences.debugDayOffset * LocalDays.DAY_MS
  override fun zone(): TimeZone = TimeZone.getDefault()
  override fun isShifted(): Boolean = preferences.debugDayOffset != 0
}
