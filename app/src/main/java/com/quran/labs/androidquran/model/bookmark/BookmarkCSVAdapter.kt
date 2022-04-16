package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.BookmarkData
import okio.BufferedSink
import java.io.IOException

@Throws(IOException::class)
fun toCSV(sink: BufferedSink, bookmarks: BookmarkData) {
  val tagsName = "${bookmarks.tags.firstOrNull()?.getCommaSeparatedNames()} \n"
  val tags = bookmarks.getTagsByLine() ?: ""
  val bookmarksName = "${bookmarks.bookmarks.firstOrNull()?.getCommaSeparatedNames()} \n"
  val bookmark = bookmarks.getBookmarksByLine() ?: ""
  val recentPageNames = "${bookmarks.recentPages.firstOrNull()?.getCommaSeparatedNames()} \n"
  val recentPages = bookmarks.getRecentPagesByLine() ?: ""

  sink.writeUtf8("Tags \n")
  sink.writeUtf8(tagsName)
  sink.writeUtf8(tags)

  sink.writeUtf8("Bookmarks \n")
  sink.writeUtf8(bookmarksName)
  sink.writeUtf8(bookmark)

  sink.writeUtf8("Recent Pages \n")
  sink.writeUtf8(recentPageNames)
  sink.writeUtf8(recentPages)
}
