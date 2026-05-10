package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BookmarkData @JvmOverloads constructor(
  val tags: List<Tag> = emptyList(),
  val bookmarks: List<Bookmark> = emptyList(),
  val recentPages: List<RecentPage> = emptyList(),
  val readingBookmark: BackupReadingBookmark? = null,
  val pageType: String? = null
) {

  fun getRecentPagesByLine() =
      recentPages
          .map { "${it.getCommaSeparatedValues()} \n" }
          .reduceOrNull { acc, recent -> "$acc$recent" }

  fun getBookmarksByLine() =
      bookmarks
          .map { "${it.getCommaSeparatedValues(tags)} \n" }
          .reduceOrNull { acc, bookmark -> "$acc$bookmark" }

  fun getReadingBookmarkByLine() =
      readingBookmark?.let { "${it.getCommaSeparatedValues()} \n" }

}
