package com.quran.mobile.progress.reminder

import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.progress.summary.BookmarkProgress
import com.quran.mobile.progress.time.LocalDays
import java.util.TimeZone

/** What the daily reminder says, if anything. */
sealed interface Reminder {
  data object None : Reminder

  /** Nothing read today; [perDay] pages a day finish the cycle by [finishByDay]. */
  data class NotReadToday(val bookmark: BookmarkProgress, val perDay: Int, val finishByDay: Long) : Reminder

  /** [pages] behind a steady pace; [perDay] pages a day still finish it by [finishByDay]. */
  data class Behind(val bookmark: BookmarkProgress, val pages: Int, val perDay: Int, val finishByDay: Long) : Reminder

  /** Past the cycle's days, with [remaining] pages to go. */
  data class Overdue(val bookmark: BookmarkProgress, val remaining: Int) : Reminder
}

/**
 * The daily reminder to finish a reading cycle in its days: it comes once a day, at the time the
 * user chose, for an open cycle of a reading bookmark with a deadline that is behind its pace or
 * wasn't read yet that day. It stays quiet while the reader is open.
 */
object ReminderDecision {

  fun decide(
    enabled: Boolean,
    nowMs: Long,
    zone: TimeZone,
    reminderMinute: Int,
    lastReminderDay: Long,
    readerOpen: Boolean,
    bookmarks: List<BookmarkProgress>,
  ): Reminder {
    val today = LocalDays.epochDay(nowMs, zone)
    if (!enabled || readerOpen || lastReminderDay == today) return Reminder.None
    if (nowMs < LocalDays.timeOnDay(today, reminderMinute, zone)) return Reminder.None
    val due = bookmarks.mapNotNull { bookmark ->
      val pace = bookmark.pace ?: return@mapNotNull null
      val readToday = bookmark.today?.let { it.day == today && (it.pages > 0 || it.minutes > 0) } == true
      when {
        pace.remaining <= 0 -> null
        pace.overdue -> Reminder.Overdue(bookmark, pace.remaining)
        pace.ahead < 0 -> Reminder.Behind(bookmark, -pace.ahead, pace.perDayNeeded, pace.finishByDay)
        !readToday -> Reminder.NotReadToday(bookmark, pace.perDayNeeded, pace.finishByDay)
        else -> null
      }
    }
    return due.minByOrNull { urgency(it) } ?: Reminder.None
  }

  /** When the reminder should next be checked: today at [reminderMinute] if that's still ahead, else tomorrow. */
  fun nextReminderAt(nowMs: Long, zone: TimeZone, reminderMinute: Int): Long {
    val today = LocalDays.epochDay(nowMs, zone)
    val todayAt = LocalDays.timeOnDay(today, reminderMinute, zone)
    return if (todayAt > nowMs) todayAt else LocalDays.timeOnDay(today + 1, reminderMinute, zone)
  }

  // overdue first, then the furthest behind, then not read today (teal before the others)
  private fun urgency(reminder: Reminder): Long = when (reminder) {
    is Reminder.Overdue -> -1_000_000L - reminder.remaining
    is Reminder.Behind -> -reminder.pages.toLong()
    is Reminder.NotReadToday -> reminder.bookmark.slot.ordinal.toLong()
    Reminder.None -> Long.MAX_VALUE
  }

  /** The slot a reminder is about. */
  fun Reminder.slot(): ReadingBookmarkType? = when (this) {
    is Reminder.Overdue -> bookmark.slot
    is Reminder.Behind -> bookmark.slot
    is Reminder.NotReadToday -> bookmark.slot
    Reminder.None -> null
  }
}
