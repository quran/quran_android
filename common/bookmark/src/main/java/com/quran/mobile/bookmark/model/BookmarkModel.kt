package com.quran.mobile.bookmark.model

import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookmarkModel @Inject constructor(bookmarksDatabase: BookmarksDatabase) {
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries

  fun bookmarksForPage(page: Int): Flow<List<Bookmark>> =
    bookmarkQueries.getBookmarksByPage(page, Mappers.bookmarkWithTagMapper)
      .asFlow()
      .mapToList()
}
