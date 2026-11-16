package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

/**
 * Reading bookmark colors. The first three are mobile-sync's slots (1, 2, and 3, in that order)
 * and sync; the others are kept on this device only, and are offered once turned on in Settings.
 *
 * Backups write the slot by its name. Moshi looks the constants up by name, so they must keep their
 * names in minified builds: @JsonClass(generateAdapter = false) is what Moshi's R8 rules keep.
 */
@JsonClass(generateAdapter = false)
enum class ReadingBookmarkType(val synced: Boolean) {
  TEAL(true), ORANGE(true), RED(true),
  GREEN(false), BLUE(false), PURPLE(false), BROWN(false), GREY(false),
}
