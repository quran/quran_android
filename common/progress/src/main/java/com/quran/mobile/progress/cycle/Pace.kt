package com.quran.mobile.progress.cycle

import kotlin.math.ceil

/**
 * Where an open cycle stands against its deadline.
 *
 * @param dayN today's day of the cycle (1 on the day it started).
 * @param pagesDone pages before the bookmark's page.
 * @param expectedByToday pages a steady pace has read by the end of today.
 * @param ahead pages ahead of that pace (negative when behind).
 * @param perDayNeeded pages a day to finish by [finishByDay], counting today.
 * @param projectedFinishDay when it finishes at the pace so far, or null before any progress.
 * @param finishByDay the last day to finish on.
 */
data class Pace(
  val dayN: Int,
  val targetDays: Int,
  val pagesDone: Int,
  val totalPages: Int,
  val expectedByToday: Int,
  val ahead: Int,
  val perDayNeeded: Int,
  val projectedFinishDay: Long?,
  val finishByDay: Long,
) {
  val remaining: Int get() = totalPages - pagesDone
  val overdue: Boolean get() = dayN > targetDays
}

object PaceMath {
  /**
   * The pace of a cycle that started on [startDay] with its bookmark now on [page].
   *
   * @param firstPage the first page of the Quran in the page type shown (pages before it are skipped).
   * @param totalPages its pages, not counting skipped ones.
   */
  fun pace(startDay: Long, today: Long, page: Int, firstPage: Int, totalPages: Int, targetDays: Int): Pace {
    val dayN = (today - startDay + 1).toInt().coerceAtLeast(1)
    val done = (page - firstPage).coerceIn(0, totalPages)
    val expected = ceil(totalPages.toDouble() * dayN.coerceAtMost(targetDays) / targetDays).toInt()
    val daysLeft = (targetDays - dayN + 1).coerceAtLeast(1)
    val remaining = totalPages - done
    val perDay = ceil(remaining.toDouble() / daysLeft).toInt()
    val projected = when {
      remaining == 0 -> today
      done == 0 -> null
      else -> today + ceil(remaining / (done.toDouble() / dayN)).toLong()
    }
    return Pace(
      dayN = dayN,
      targetDays = targetDays,
      pagesDone = done,
      totalPages = totalPages,
      expectedByToday = expected,
      ahead = done - expected,
      perDayNeeded = perDay,
      projectedFinishDay = projected,
      finishByDay = startDay + targetDays - 1,
    )
  }
}
