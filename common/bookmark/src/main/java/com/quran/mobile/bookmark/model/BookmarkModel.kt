package com.quran.mobile.bookmark.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class BookmarkModel @Inject constructor(bookmarksDatabase: BookmarksDatabase) {
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries

  fun bookmarksForPage(page: Int): Flow<List<Bookmark>> =
    bookmarkQueries.getBookmarksByPage(page, Mappers.bookmarkWithTagMapper)
      .asFlow()
      .mapToList(Dispatchers.IO)

  suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Pair<SuraAyah, Boolean> {
    val bookmarkIds = bookmarkQueries.getBookmarkIdForSuraAyah(suraAyah.sura, suraAyah.ayah)
      .asFlow()
      // was .mapToOneOrNull, but some people have multiple bookmarks for the same ayah
      // should try to figure out why at some point or otherwise de-duplicate them
      .mapToList(Dispatchers.IO)
      .first()
    return suraAyah to bookmarkIds.isNotEmpty()
  }
}
