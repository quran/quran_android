package com.quran.data.core

import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType

interface ReadingBookmarkUpdater {
  fun placeReadingBookmark(slot: ReadingBookmarkType, target: ReadingBookmarkTarget)
  fun clearReadingBookmark(slot: ReadingBookmarkType)
}
