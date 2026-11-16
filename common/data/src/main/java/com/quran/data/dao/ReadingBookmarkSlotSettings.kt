package com.quran.data.dao

import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import kotlinx.coroutines.flow.Flow

/** Which reading bookmark slots are on, and what each is for. Kept on the device. */
interface ReadingBookmarkSlotSettings {
  /** Every slot's setting, in slot order. */
  fun settingsFlow(): Flow<List<ReadingBookmarkSlotSetting>>
  suspend fun settings(): List<ReadingBookmarkSlotSetting>
  suspend fun setEnabled(slot: ReadingBookmarkType, enabled: Boolean)
  suspend fun setPurpose(slot: ReadingBookmarkType, purpose: ReadingBookmarkPurpose)
  suspend fun setTargetDays(slot: ReadingBookmarkType, days: Int)
}
