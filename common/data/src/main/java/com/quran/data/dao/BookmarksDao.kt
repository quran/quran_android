package com.quran.data.dao

import com.quran.data.model.bookmark.Bookmark

interface BookmarksDao {
  suspend fun bookmarks(): List<Bookmark>
  suspend fun replaceBookmarks(bookmarks: List<Bookmark>)
}
