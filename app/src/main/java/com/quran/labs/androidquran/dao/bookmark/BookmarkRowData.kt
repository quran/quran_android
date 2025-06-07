package com.quran.labs.androidquran.dao.bookmark

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag

sealed class BookmarkRowData {
  data class RecentPageHeader(val count: Int) : BookmarkRowData()
  data class RecentPage(val recentPage: com.quran.data.model.bookmark.RecentPage) : BookmarkRowData()
  data class TagHeader(val tag: Tag) : BookmarkRowData()
  data class BookmarkItem(val bookmark: Bookmark, val tagId: Long? = null) : BookmarkRowData()
  object PageBookmarksHeader : BookmarkRowData()
  object AyahBookmarksHeader : BookmarkRowData()
  object NotTaggedHeader : BookmarkRowData()
}