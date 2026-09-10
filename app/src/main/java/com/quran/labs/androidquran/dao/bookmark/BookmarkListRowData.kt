package com.quran.labs.androidquran.dao.bookmark

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor

sealed class BookmarkListMode {
  data class Collection(val collectionId: String) : BookmarkListMode()
  data class Highlights(val color: HighlightColor) : BookmarkListMode()
}

sealed class BookmarkListRowData {
  data class SuraHeader(val sura: Int) : BookmarkListRowData()
  data class BookmarkItem(val bookmark: Bookmark, val collectionId: String) : BookmarkListRowData()
  data class HighlightItem(
    val highlight: Highlight,
    val ayahText: String?
  ) : BookmarkListRowData()
}
