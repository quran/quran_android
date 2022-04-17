package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.BookmarkData
import okio.BufferedSink
import java.io.IOException

@Throws(IOException::class)
fun toCSV(sink: BufferedSink, bookmarks: BookmarkData) {
  val bookmarksName = "${bookmarks.bookmarks.firstOrNull()?.getCommaSeparatedNames()} \n"
  val bookmark = bookmarks.getBookmarksByLine() ?: ""
  val recentPages = bookmarks.getRecentPagesByLine() ?: ""

  sink.writeUtf8(bookmarksName)
  sink.writeUtf8(bookmark)
  sink.writeUtf8(recentPages)
}
