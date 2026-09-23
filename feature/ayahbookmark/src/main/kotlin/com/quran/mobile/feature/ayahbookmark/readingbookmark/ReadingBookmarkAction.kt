package com.quran.mobile.feature.ayahbookmark.readingbookmark

import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType

sealed interface ReadingBookmarkAction {
  data class Place(
    val slot: ReadingBookmarkType,
    val target: ReadingBookmarkTarget
  ) : ReadingBookmarkAction

  data class Clear(val slot: ReadingBookmarkType) : ReadingBookmarkAction
}
