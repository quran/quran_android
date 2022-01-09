package com.quran.data.dao

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage

interface BookmarksDao {
  suspend fun bookmarks(): List<Bookmark>
  suspend fun replaceBookmarks(bookmarks: List<Bookmark>)

  // recent pages
  suspend fun recentPages(): List<RecentPage>
  suspend fun removeRecentPages()
  suspend fun replaceRecentPages(pages: List<RecentPage>)
}
