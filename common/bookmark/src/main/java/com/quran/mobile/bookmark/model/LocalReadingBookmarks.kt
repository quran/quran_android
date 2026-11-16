package com.quran.mobile.bookmark.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.Local_reading_bookmark
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The reading bookmarks past mobile-sync's three slots, kept in the bookmarks database on this
 * device. A page bookmark keeps its page type and first ayah too, so it is shown on the page with
 * that ayah in another page type, as mobile-sync maps its own.
 */
@SingleIn(AppScope::class)
class LocalReadingBookmarks @Inject constructor(
  database: BookmarksDatabase,
  private val pageMapper: ReadingBookmarkPageMapper,
) {
  private val queries = database.localReadingBookmarkQueries

  fun rowsFlow(): Flow<List<Local_reading_bookmark>> =
    queries.localReadingBookmarks().asFlow().mapToList(Dispatchers.IO)

  suspend fun rows(): List<Local_reading_bookmark> = withContext(Dispatchers.IO) {
    queries.localReadingBookmarks().executeAsList()
  }

  /** [rows] as bookmarks of [pageType], for the [enabled] slots, in slot order. */
  fun toReadingBookmarks(
    rows: List<Local_reading_bookmark>,
    enabled: Set<ReadingBookmarkType>,
    pageType: String,
  ): List<ReadingBookmark> {
    val bySlot = rows.associateBy { it.slot }
    return ReadingBookmarkType.entries.filter { !it.synced && it in enabled }.map { slot ->
      val row = bySlot[slot.name]
      val timestamp = Instant.fromEpochMilliseconds(row?.updated_at ?: 0L)
      val sura = row?.sura?.toInt()
      val ayah = row?.ayah?.toInt()
      when {
        row == null || sura == null || ayah == null -> EmptyReadingBookmark(slot, timestamp, row?.name)
        row.page != null -> PageReadingBookmark(slot, pageOf(row, pageType), timestamp, row.name)
        else -> AyahReadingBookmark(slot, sura, ayah, timestamp, row.name)
      }
    }
  }

  suspend fun setPage(slot: ReadingBookmarkType, page: Int, pageType: String, atMs: Long) {
    val first = pageMapper.sourcePageToSuraAyah(page, pageType)
    write(slot, atMs) { queries.setLocalPosition(first.sura.toLong(), first.ayah.toLong(), page.toLong(), pageType, atMs, slot.name) }
  }

  suspend fun setAyah(slot: ReadingBookmarkType, suraAyah: SuraAyah, atMs: Long) {
    write(slot, atMs) { queries.setLocalPosition(suraAyah.sura.toLong(), suraAyah.ayah.toLong(), null, null, atMs, slot.name) }
  }

  suspend fun clear(slot: ReadingBookmarkType, atMs: Long): ReadingBookmark {
    write(slot, atMs) { queries.setLocalPosition(null, null, null, null, atMs, slot.name) }
    val name = rows().firstOrNull { it.slot == slot.name }?.name
    return EmptyReadingBookmark(slot, Instant.fromEpochMilliseconds(atMs), name)
  }

  suspend fun rename(slot: ReadingBookmarkType, name: String?, atMs: Long) {
    write(slot, atMs) { queries.renameLocalReadingBookmark(name?.takeIf { it.isNotBlank() }, slot.name) }
  }

  /** Whether [slot] is a page bookmark on [page] of [pageType]. */
  suspend fun isOnPage(slot: ReadingBookmarkType, page: Int, pageType: String): Boolean {
    val row = rows().firstOrNull { it.slot == slot.name } ?: return false
    return row.page != null && row.sura != null && pageOf(row, pageType) == page
  }

  private fun pageOf(row: Local_reading_bookmark, pageType: String): Int {
    val page = row.page!!.toInt()
    return if (row.page_type == pageType) page else pageMapper.suraAyahToPage(row.sura!!.toInt(), row.ayah!!.toInt(), pageType)
  }

  private suspend fun write(slot: ReadingBookmarkType, atMs: Long, block: () -> Unit) {
    require(!slot.synced) { "$slot is kept by mobile-sync" }
    withContext(Dispatchers.IO) {
      queries.transaction {
        queries.ensureLocalReadingBookmark(slot.name, atMs)
        block()
      }
    }
  }
}
