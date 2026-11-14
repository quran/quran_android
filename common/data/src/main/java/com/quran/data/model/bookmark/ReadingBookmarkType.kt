package com.quran.data.model.bookmark

/**
 * Reading bookmark colors. The first three are mobile-sync's slots (1, 2, and 3, in that order)
 * and sync; the others are kept on this device only, and are offered once turned on in Settings.
 */
enum class ReadingBookmarkType(val synced: Boolean) {
  TEAL(true), ORANGE(true), RED(true),
  GREEN(false), BLUE(false), PURPLE(false), BROWN(false), GREY(false),
}
