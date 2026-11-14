package com.quran.mobile.progress.reading

import com.quran.data.di.AppScope
import com.quran.mobile.progress.db.ProgressDatabase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores reading time (see ReadingTime.sq). */
@SingleIn(AppScope::class)
class ReadingTimeLog @Inject constructor(private val database: ProgressDatabase) {

  /** Adds [times] spent on [pageType]'s pages, in a stretch that ended at [atMs]. */
  suspend fun add(times: List<PageTime>, pageType: String, atMs: Long) {
    if (times.isEmpty()) return
    withContext(Dispatchers.IO) {
      database.readingTimeQueries.transaction {
        times.filter { it.ms > 0 }.forEach {
          database.readingTimeQueries.insertTime(it.day, atMs, pageType, it.page.toLong(), it.ms, if (it.read) 1L else 0L)
        }
      }
    }
  }
}
