package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BookmarkData(val tags: List<Tag> = emptyList(),
                        val bookmarks: List<Bookmark> = emptyList(),
                        val recentPages: List<RecentPage> = emptyList()) {

  fun getRecentPagesByLine() =
      recentPages
          .map { "${it.getCommaSeparatedValues()} \n" }
          .reduceOrNull { acc, recent -> "$acc$recent" }

  fun getBookmarksByLine() =
      bookmarks
          .map { "${it.getCommaSeparatedValues(tags)} \n" }
          .reduceOrNull { acc, bookmark -> "$acc$bookmark" }

}
