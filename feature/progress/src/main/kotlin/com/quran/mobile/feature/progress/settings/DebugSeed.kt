package com.quran.mobile.feature.progress.settings

import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.progress.history.DebugHistory
import com.quran.mobile.progress.history.ProgressHistory
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject

/** Debuggable builds only: replaces Progress's history with 12 days of teal's reading. */
class DebugSeed @Inject constructor(
  private val history: ProgressHistory,
  private val debugHistory: DebugHistory,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val settings: Settings,
  private val quranInfoProvider: () -> QuranInfo,
  private val clock: ProgressClock,
) {
  suspend fun seed() {
    history.clear()
    val page = debugHistory.seed(
      slot = ReadingBookmarkType.TEAL.name,
      today = clock.today(),
      zone = clock.zone(),
      quranInfo = quranInfoProvider(),
      pageType = settings.pageType(),
      pages = listOf(16, 22, 19, 26, 10, 0, 24, 21, 27, 15, 25, 24),
      minutes = listOf(34, 49, 38, 55, 22, 3, 52, 42, 59, 31, 54, 58),
    )
    // the bookmark itself where its history ends, so the next move is measured from there
    readingBookmarksDao.setPageReadingBookmark(ReadingBookmarkType.TEAL, page)
  }
}
