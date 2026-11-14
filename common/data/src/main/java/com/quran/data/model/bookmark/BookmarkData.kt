package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BookmarkData @JvmOverloads constructor(
  val tags: List<Tag> = emptyList(),
  val bookmarks: List<Bookmark> = emptyList(),
  val recentPages: List<RecentPage> = emptyList(),
  /** The synced reading bookmarks (older versions read only these three slots). */
  val readingBookmarks: List<BackupReadingBookmark> = emptyList(),
  val pageType: String? = null,
  /** The reading bookmarks kept on the device only, which older versions skip. */
  val localReadingBookmarks: List<BackupReadingBookmark> = emptyList(),
) {

  fun getCommaSeparatedNames() =
    "type, sura, ayah, page, timestamp, tags, slot"

  fun getRecentPagesByLine() =
    recentPages
      .map { "${it.getCommaSeparatedValues()} \n" }
      .reduceOrNull { acc, recent -> "$acc$recent" }

  fun getBookmarksByLine() =
    bookmarks
      .map { "${it.getCommaSeparatedValues(tags)} \n" }
      .reduceOrNull { acc, bookmark -> "$acc$bookmark" }

  fun getReadingBookmarksByLine() =
    (readingBookmarks + localReadingBookmarks)
      .map { "${it.getCommaSeparatedValues()} \n" }
      .reduceOrNull { acc, readingBookmark -> "$acc$readingBookmark" }
}
