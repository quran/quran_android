package com.quran.mobile.bookmark.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.Reading_bookmark_setting
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reading bookmark slot settings, kept in the bookmarks database. By default the three synced
 * slots are on and for reading, teal with a 30-day cycle; the others are off.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ReadingBookmarkSlotSettingsImpl @Inject constructor(database: BookmarksDatabase) : ReadingBookmarkSlotSettings {
  private val queries = database.localReadingBookmarkQueries

  override fun settingsFlow(): Flow<List<ReadingBookmarkSlotSetting>> {
    return queries.slotSettings().asFlow().mapToList(Dispatchers.IO).map { merge(it) }.distinctUntilChanged()
  }

  override suspend fun settings(): List<ReadingBookmarkSlotSetting> = withContext(Dispatchers.IO) {
    merge(queries.slotSettings().executeAsList())
  }

  override suspend fun setEnabled(slot: ReadingBookmarkType, enabled: Boolean) {
    // the synced slots are always offered
    if (slot.synced) return
    update(slot) { it.copy(enabled = enabled) }
  }

  override suspend fun setPurpose(slot: ReadingBookmarkType, purpose: ReadingBookmarkPurpose) {
    update(slot) { it.copy(purpose = purpose) }
  }

  override suspend fun setTargetDays(slot: ReadingBookmarkType, days: Int) {
    update(slot) { it.copy(targetDays = days.coerceIn(0, MAX_TARGET_DAYS)) }
  }

  private suspend fun update(slot: ReadingBookmarkType, change: (ReadingBookmarkSlotSetting) -> ReadingBookmarkSlotSetting) {
    withContext(Dispatchers.IO) {
      queries.transaction {
        val current = merge(queries.slotSettings().executeAsList()).first { it.slot == slot }
        val updated = change(current)
        queries.setSlotSetting(
          slot.name, if (updated.enabled) 1L else 0L, updated.purpose.name, updated.targetDays.toLong()
        )
      }
    }
  }

  companion object {
    const val MAX_TARGET_DAYS = 366

    fun defaultSetting(slot: ReadingBookmarkType): ReadingBookmarkSlotSetting = ReadingBookmarkSlotSetting(
      slot = slot,
      enabled = slot.synced,
      purpose = ReadingBookmarkPurpose.READING,
      targetDays = if (slot == ReadingBookmarkType.TEAL) ReadingBookmarkSlotSetting.DEFAULT_TARGET_DAYS else 0,
    )

    private fun merge(rows: List<Reading_bookmark_setting>): List<ReadingBookmarkSlotSetting> {
      val bySlot = rows.associateBy { it.slot }
      return ReadingBookmarkType.entries.map { slot ->
        val row = bySlot[slot.name] ?: return@map defaultSetting(slot)
        ReadingBookmarkSlotSetting(
          slot = slot,
          enabled = slot.synced || row.enabled != 0L,
          purpose = ReadingBookmarkPurpose.entries.firstOrNull { it.name == row.purpose } ?: ReadingBookmarkPurpose.READING,
          targetDays = row.target_days.toInt(),
        )
      }
    }
  }
}
