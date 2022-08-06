package com.quran.mobile.bookmark.model

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class BookmarkModel @Inject constructor(bookmarksDatabase: BookmarksDatabase) {
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries

  fun bookmarksForPage(page: Int): Flow<List<Bookmark>> =
    bookmarkQueries.getBookmarksByPage(page, Mappers.bookmarkWithTagMapper)
      .asFlow()
      .mapToList()

  suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Pair<SuraAyah, Boolean> {
    val bookmarkId = bookmarkQueries.getBookmarkIdForSuraAyah(suraAyah.sura, suraAyah.ayah)
      .asFlow()
      .mapToOneOrNull()
      .first()
    return suraAyah to (bookmarkId != null)
  }
}
