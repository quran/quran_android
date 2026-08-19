package com.quran.labs.androidquran.dao.bookmark

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.highlight.HighlightColor

sealed class BookmarkRowData {
  data object ReadingBookmarkHeader : BookmarkRowData()
  data class ReadingBookmarkItem(val readingBookmark: ReadingBookmark) : BookmarkRowData()

  data class RecentPageHeader(val count: Int) : BookmarkRowData()
  data class RecentPage(val recentPage: com.quran.data.model.bookmark.RecentPage) : BookmarkRowData()

  data object HighlightsHeader : BookmarkRowData()
  data class HighlightColorItem(val color: HighlightColor, val count: Int) : BookmarkRowData()

  data class TagHeader(
    val tag: Tag,
    val count: Int = 0,
    val isCollapsed: Boolean = false
  ) : BookmarkRowData() {
    fun withCountDelta(delta: Int): TagHeader =
      if (delta == 0) this else copy(count = (count + delta).coerceAtLeast(0))
  }

  data class BookmarkItem(val bookmark: Bookmark, val tagId: String? = null) : BookmarkRowData()
  object PageBookmarksHeader : BookmarkRowData()
  object AyahBookmarksHeader : BookmarkRowData()
  data class NotTaggedHeader(
    val count: Int = 0,
    val isCollapsed: Boolean = false
  ) : BookmarkRowData() {
    fun withCountDelta(delta: Int): NotTaggedHeader =
      if (delta == 0) this else copy(count = (count + delta).coerceAtLeast(0))
  }
}
