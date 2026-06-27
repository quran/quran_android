package com.quran.mobile.bookmark.legacy

/**
 * Converts numeric IDs from the deprecated bookmarks database into runtime string IDs while
 * keeping the original values stable for migration lookup keys.
 */
internal object LegacyBookmarkIds {
  fun bookmarkId(id: Long): String = id.toString()

  fun tagId(id: Long): String = id.toString()
}
