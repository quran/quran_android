package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass
import kotlin.time.Instant

@JsonClass(generateAdapter = true)
data class BackupReadingBookmark(
  val type: String,
  val sura: Int? = null,
  val ayah: Int? = null,
  val page: Int? = null,
  val slot: ReadingBookmarkType,
  val timestamp: Instant,
  val name: String? = null
) {
  fun getCommaSeparatedValues() =
    "reading_bookmark, $sura, $ayah, $page, ${timestamp.epochSeconds},, $slot"

  companion object {
    const val TYPE_AYAH = "ayah"
    const val TYPE_PAGE = "page"

    fun fromReadingBookmark(
      readingBookmark: ReadingBookmark,
      ayahPageResolver: (sura: Int, ayah: Int) -> Int
    ): BackupReadingBookmark? {
      return when (readingBookmark) {
        is AyahReadingBookmark -> BackupReadingBookmark(
          type = TYPE_AYAH,
          slot = readingBookmark.slot,
          sura = readingBookmark.sura,
          ayah = readingBookmark.ayah,
          page = ayahPageResolver(readingBookmark.sura, readingBookmark.ayah),
          timestamp = readingBookmark.timestamp,
          name = readingBookmark.name
        )
        is PageReadingBookmark -> BackupReadingBookmark(
          type = TYPE_PAGE,
          slot = readingBookmark.slot,
          page = readingBookmark.page,
          timestamp = readingBookmark.timestamp,
          name = readingBookmark.name
        )

        is EmptyReadingBookmark -> null
      }
    }
  }
}
