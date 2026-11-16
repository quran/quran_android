package com.quran.mobile.progress.log

import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.mobile.progress.db.Bookmark_move
import com.quran.mobile.progress.db.ProgressDatabase
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the history of every reading bookmark's position (see BookmarkMove.sq), whoever moves it:
 * the reader, a backup, or sync from another device (also while the app wasn't running: each
 * position is compared with the last one stored, not with the last one seen). A move also credits
 * the reading time not yet credited to that bookmark.
 */
@SingleIn(AppScope::class)
class BookmarkMoveRecorder @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val settings: Settings,
  private val quranInfoProvider: () -> QuranInfo,
  private val database: ProgressDatabase,
  private val clock: ProgressClock,
  private val scope: AppCoroutineScope,
) {
  private val mutex = Mutex()
  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = scope.launch {
      while (isActive) {
        try {
          readingBookmarksDao.readingBookmarksFlow().collect { record(it) }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // e.g. before the first start chose a page type: try again once a setting changes
          settings.preferencesFlow().firstOrNull() ?: break
        }
      }
    }
  }

  /** Stores what changed in [bookmarks] since the last rows. */
  suspend fun record(bookmarks: List<ReadingBookmark>): Unit = mutex.withLock {
    val pageType = settings.pageType()
    val quranInfo = quranInfoProvider()
    withContext(Dispatchers.IO) {
      val queries = database.bookmarkMoveQueries
      queries.transaction {
        for (bookmark in bookmarks) {
          val slot = bookmark.slot.name
          val position = bookmark.position(quranInfo, pageType)
          val last = queries.lastMove(slot).executeAsOneOrNull()
          val kind = when {
            last == null -> if (position == null) null else MoveKind.BASELINE
            position == null -> if (last.kind == MoveKind.CLEAR.name) null else MoveKind.CLEAR
            last.kind == MoveKind.CLEAR.name || last.kind == MoveKind.CYCLE_MARK.name ->
              when (val before = queries.lastPosition(slot).executeAsOneOrNull()) {
                null -> MoveKind.BASELINE
                else -> movedFrom(before.toPosition(), position)
              }
            else -> movedFrom(last.toPosition(), position)
          } ?: continue
          val atMs = atMs(bookmark, last?.at_ms)
          queries.insertMove(
            slot, atMs, clock.dayOf(atMs), kind.name,
            position?.page?.toLong(), position?.pageType, position?.sura?.toLong(), position?.ayah?.toLong()
          )
          if (kind == MoveKind.MOVE) {
            database.readingTimeQueries.creditUpTo(slot, atMs)
          }
        }
      }
    }
  }

  /** Starts a new cycle of [slot] now. */
  suspend fun markCycleStart(slot: String) {
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val now = clock.nowMs()
        database.bookmarkMoveQueries.insertMove(slot, now, clock.dayOf(now), MoveKind.CYCLE_MARK.name, null, null, null, null)
      }
    }
  }

  private fun movedFrom(before: BookmarkPosition?, now: BookmarkPosition): MoveKind? = when {
    before == null -> MoveKind.BASELINE
    // pages of another page type can't be compared: measure again from here
    before.pageType != now.pageType -> MoveKind.BASELINE
    before.page == now.page && before.sura == now.sura && before.ayah == now.ayah -> null
    else -> MoveKind.MOVE
  }

  /**
   * When the bookmark moved: its own time when that's after the last row (a move made on another
   * device, or while the app was closed), but never in the future; now when the clock was moved.
   */
  private fun atMs(bookmark: ReadingBookmark, lastAt: Long?): Long {
    val now = clock.nowMs()
    if (clock.isShifted()) return maxOf(now, lastAt ?: 0L)
    val own = bookmark.timestamp.toEpochMilliseconds()
    return own.coerceIn(lastAt ?: 0L, maxOf(now, lastAt ?: 0L))
  }

  private fun Bookmark_move.toPosition(): BookmarkPosition? {
    val page = page ?: return null
    return BookmarkPosition(page.toInt(), page_type.orEmpty(), sura?.toInt() ?: 0, ayah?.toInt() ?: 0)
  }
}

/** Where [this] is in [pageType] (whose [quranInfo] is given), or null when it isn't placed. */
fun ReadingBookmark.position(quranInfo: QuranInfo, pageType: String): BookmarkPosition? = when (this) {
  is PageReadingBookmark -> {
    val bounds = quranInfo.getPageBounds(page)
    BookmarkPosition(page, pageType, bounds[0], bounds[1])
  }
  is AyahReadingBookmark -> BookmarkPosition(quranInfo.getPageFromSuraAyah(sura, ayah), pageType, sura, ayah)
  is EmptyReadingBookmark -> null
}

/** This row of the history, as a [RecordedMove]. */
fun Bookmark_move.toRecorded(): RecordedMove = RecordedMove(
  atMs = at_ms,
  day = local_day,
  kind = MoveKind.valueOf(kind),
  position = page?.let { BookmarkPosition(it.toInt(), page_type.orEmpty(), sura?.toInt() ?: 0, ayah?.toInt() ?: 0) },
)
