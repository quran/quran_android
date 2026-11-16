package com.quran.mobile.progress.history

import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.mobile.progress.db.ProgressDatabase
import com.quran.mobile.progress.log.MoveKind
import com.quran.mobile.progress.time.LocalDays
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Debuggable builds only: fills in days of reading, to try Progress out without waiting for them. */
@SingleIn(AppScope::class)
class DebugHistory @Inject constructor(private val database: ProgressDatabase) {

  /**
   * Starts a cycle of [slot] at Al-Fatiha [pages] days before [today], and for each day after moves
   * it on by that day's pages, with that day's minutes of reading credited to it. Returns the page
   * it ends on.
   */
  suspend fun seed(
    slot: String,
    today: Long,
    zone: TimeZone,
    quranInfo: QuranInfo,
    pageType: String,
    pages: List<Int>,
    minutes: List<Int>,
  ): Int = withContext(Dispatchers.IO) {
    val first = today - pages.size + 1
    var page = 1 + quranInfo.skip
    database.transaction {
      val moves = database.bookmarkMoveQueries
      val start = LocalDays.timeOnDay(first, 7 * 60, zone)
      moves.insertMove(slot, start, first, MoveKind.BASELINE.name, page.toLong(), pageType, 1L, 1L)
      pages.forEachIndexed { index, count ->
        val day = first + index
        val readAt = LocalDays.timeOnDay(day, 20 * 60, zone)
        val movedAt = LocalDays.timeOnDay(day, 21 * 60, zone)
        if (minutes[index] > 0) {
          database.readingTimeQueries.insertTime(day, readAt, pageType, page.toLong(), minutes[index] * 60_000L, 1L)
        }
        if (count > 0) {
          page = (page + count).coerceAtMost(quranInfo.numberOfPages)
          val bounds = quranInfo.getPageBounds(page)
          moves.insertMove(slot, movedAt, day, MoveKind.MOVE.name, page.toLong(), pageType, bounds[0].toLong(), bounds[1].toLong())
          database.readingTimeQueries.creditUpTo(slot, movedAt)
        }
      }
    }
    page
  }
}
