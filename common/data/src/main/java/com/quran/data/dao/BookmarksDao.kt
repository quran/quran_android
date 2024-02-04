package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import kotlinx.coroutines.flow.Flow

interface BookmarksDao {
  suspend fun bookmarks(): List<Bookmark>
  fun bookmarksForPage(page: Int): Flow<List<Bookmark>>
  suspend fun replaceBookmarks(bookmarks: List<Bookmark>)
  suspend fun removeBookmarksForPage(page: Int)
  suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean

  // recent pages
  suspend fun recentPages(): List<RecentPage>
  suspend fun removeRecentPages()
  suspend fun replaceRecentPages(pages: List<RecentPage>)
  suspend fun removeRecentsForPage(page: Int)
}
