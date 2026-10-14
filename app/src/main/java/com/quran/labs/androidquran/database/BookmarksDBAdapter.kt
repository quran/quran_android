package com.quran.labs.androidquran.database

import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import com.quran.mobile.bookmark.mapper.convergeCommonlyTagged
import com.quran.mobile.bookmark.migration.LegacyBookmarkTag
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Deprecated("Only used for one-time legacy bookmark migration reads")
@SingleIn(AppScope::class)
class BookmarksDBAdapter @Inject constructor(bookmarksDatabase: BookmarksDatabase) {
  private val tagQueries = bookmarksDatabase.tagQueries
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries
  private val lastPageQueries = bookmarksDatabase.lastPageQueries

  fun getBookmarksForMigration(): List<Bookmark> {
    return bookmarkQueries.getBookmarksByDateAdded(Mappers.bookmarkWithTagMapper)
      .executeAsList()
      .convergeCommonlyTagged()
  }

  fun getRecentPages(): List<RecentPage> {
    return lastPageQueries.getLastPages(Mappers.recentPageMapper)
      .executeAsList()
  }

  fun getTagsForMigration(): List<LegacyBookmarkTag> {
    return tagQueries.getTags { id, name, addedDate ->
      LegacyBookmarkTag(id, name, addedDate)
    }.executeAsList()
  }
}
