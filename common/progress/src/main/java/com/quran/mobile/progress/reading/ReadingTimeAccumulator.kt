package com.quran.mobile.progress.reading

import com.quran.mobile.progress.time.LocalDays
import java.util.TimeZone

/** Time on one page on one local day. [read] once the page was on screen long enough to count. */
data class PageTime(val day: Long, val page: Int, val ms: Long, val read: Boolean)

/**
 * Adds up the time Quran pages are on screen while the reader is open, by wall time.
 *
 * Time counts only up to [idleMs] after the last page turn or touch (the reader keeps the screen
 * on, so a page left open isn't being read). Two pages side by side share the time, so the day's
 * total is the time spent. A page counts as read once it was on screen for [readMs]. If the clock
 * goes back, that stretch is dropped; time is split at local midnight.
 */
class ReadingTimeAccumulator(
  private val zone: () -> TimeZone,
  private val idleMs: Long = IDLE_MS,
  private val readMs: Long = READ_MS,
) {
  private var open = false
  private var pages: Set<Int> = emptySet()
  private var lastAt = 0L
  private var lastActivity = 0L
  // continuous time each page shown now has been on screen
  private val onScreen = HashMap<Int, Long>()
  private val pending = LinkedHashMap<Pair<Long, Int>, PageTime>()

  fun onResume(now: Long, visible: Set<Int>) {
    open = true
    pages = visible
    lastAt = now
    lastActivity = now
    onScreen.clear()
  }

  fun onPages(now: Long, visible: Set<Int>) {
    advance(now)
    if (visible != pages) {
      onScreen.keys.retainAll(visible)
      pages = visible
    }
    lastActivity = now
  }

  fun onInteraction(now: Long) {
    advance(now)
    lastActivity = now
  }

  fun tick(now: Long) = advance(now)

  fun onPause(now: Long) {
    advance(now)
    open = false
    onScreen.clear()
  }

  /** What was counted since the last drain. */
  fun drain(): List<PageTime> {
    val drained = pending.values.toList()
    pending.clear()
    return drained
  }

  private fun advance(now: Long) {
    if (!open || pages.isEmpty() || now <= lastAt) {
      lastAt = now
      return
    }
    var from = lastAt
    val until = minOf(now, lastActivity + idleMs)
    val tz = zone()
    while (from < until) {
      val day = LocalDays.epochDay(from, tz)
      val to = minOf(until, LocalDays.startOfDayMs(day + 1, tz))
      add(day, to - from)
      from = to
    }
    lastAt = now
  }

  private fun add(day: Long, ms: Long) {
    val share = ms / pages.size
    for (page in pages) {
      val seen = (onScreen[page] ?: 0L) + ms
      onScreen[page] = seen
      val key = day to page
      val before = pending[key]
      pending[key] = PageTime(
        day = day,
        page = page,
        ms = (before?.ms ?: 0L) + share,
        read = before?.read == true || seen >= readMs,
      )
    }
  }

  companion object {
    const val IDLE_MS = 3 * 60_000L
    const val READ_MS = 20_000L
  }
}
