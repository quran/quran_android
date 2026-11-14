package com.quran.mobile.progress.cycle

import com.quran.mobile.progress.log.BookmarkPosition
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.log.RecordedMove

/** How far a bookmark moved forward each day. */
object DailyProgress {

  /**
   * Pages the bookmark moved forward on each local day of [cycle], from its history [moves].
   *
   * Only the net move of each day counts (going back and forward again counts once), and never
   * below 0. A BASELINE (a change of page type) starts measuring again from where it is. A cycle
   * that ended with a wrap counts the pages to the end of the Quran on the day it wrapped.
   *
   * @param pageOf a position's page in the page type being shown.
   * @param firstPage the first page of the Quran in it, where a cycle that wrapped starts.
   * @param lastPage its last page.
   */
  fun pagesPerDay(
    cycle: Cycle,
    moves: List<RecordedMove>,
    pageOf: (BookmarkPosition) -> Int,
    firstPage: Int,
    lastPage: Int,
  ): Map<Long, Int> {
    val pages = LinkedHashMap<Long, Int>()
    // where the bookmark was at the cycle's start
    var start: Int? = if (cycle.partial) null else firstPage
    var end: Int? = start
    var day = cycle.startDay
    fun close() {
      val from = start ?: return
      val to = end ?: return
      pages[day] = (pages[day] ?: 0) + (to - from).coerceAtLeast(0)
    }
    for (move in moves) {
      if (move.atMs < cycle.startMs) continue
      val ended = cycle.endMs
      if (ended != null && move.atMs >= ended) break
      if (move.day != day) {
        close()
        day = move.day
        start = end
      }
      val position = move.position ?: continue
      val page = pageOf(position)
      when (move.kind) {
        MoveKind.BASELINE -> {
          close()
          start = page
          end = page
        }
        MoveKind.MOVE -> {
          if (start == null) start = page
          end = page
        }
        else -> Unit
      }
    }
    if (cycle.wrapped) {
      // the reading went on to the end before the bookmark went back to the start
      if (cycle.endDay != day) {
        close()
        day = cycle.endDay!!
        start = end
      }
      end = lastPage + 1
    }
    close()
    return pages
  }
}
