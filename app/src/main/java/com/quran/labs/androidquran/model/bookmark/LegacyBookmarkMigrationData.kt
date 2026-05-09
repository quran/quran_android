package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage

data class LegacyBookmarkTag(
  val id: Long,
  val name: String,
  val timestamp: Long
)

data class LegacyBookmarksSnapshot(
  val tags: List<LegacyBookmarkTag>,
  val bookmarks: List<Bookmark>,
  val recentPages: List<RecentPage>
) {
  fun isEmpty(): Boolean = tags.isEmpty() && bookmarks.isEmpty() && recentPages.isEmpty()
}
