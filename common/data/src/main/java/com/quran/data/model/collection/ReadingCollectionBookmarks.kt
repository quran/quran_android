package com.quran.data.model.collection

import com.quran.data.model.bookmark.AyahBookmark

data class ReadingCollectionBookmarks(
  val readingCollection: ReadingCollection,
  val bookmarks: List<AyahBookmark>
)
