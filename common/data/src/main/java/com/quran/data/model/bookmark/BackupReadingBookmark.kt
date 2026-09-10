package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupReadingBookmark(
  val type: String,
  val sura: Int? = null,
  val ayah: Int? = null,
  val page: Int? = null,
  val timestamp: Long = System.currentTimeMillis() / 1000
) {
  fun getCommaSeparatedValues() =
    "reading_bookmark, $sura, $ayah, $page, $timestamp"

  companion object {
    const val TYPE_AYAH = "ayah"
    const val TYPE_PAGE = "page"

    fun fromReadingBookmark(
      readingBookmark: ReadingBookmark,
      ayahPageResolver: (sura: Int, ayah: Int) -> Int
    ): BackupReadingBookmark {
      return when (readingBookmark) {
        is AyahReadingBookmark -> BackupReadingBookmark(
          type = TYPE_AYAH,
          sura = readingBookmark.sura,
          ayah = readingBookmark.ayah,
          page = ayahPageResolver(readingBookmark.sura, readingBookmark.ayah),
          timestamp = readingBookmark.timestamp
        )
        is PageReadingBookmark -> BackupReadingBookmark(
          type = TYPE_PAGE,
          page = readingBookmark.page,
          timestamp = readingBookmark.timestamp
        )
      }
    }
  }
}
