package com.quran.data.model.bookmark

/**
 * How the user set up one reading bookmark slot.
 *
 * @param enabled whether the slot is offered (the three synced ones always are).
 * @param targetDays days to read the Quran in, for a [ReadingBookmarkPurpose.READING] bookmark; 0 for
 * no deadline.
 */
data class ReadingBookmarkSlotSetting(
  val slot: ReadingBookmarkType,
  val enabled: Boolean,
  val purpose: ReadingBookmarkPurpose = ReadingBookmarkPurpose.READING,
  val targetDays: Int = DEFAULT_TARGET_DAYS,
) {
  companion object {
    const val DEFAULT_TARGET_DAYS = 30
  }
}
