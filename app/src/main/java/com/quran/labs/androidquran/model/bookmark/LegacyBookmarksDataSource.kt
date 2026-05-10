package com.quran.labs.androidquran.model.bookmark

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Companion.SORT_DATE_ADDED
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.LegacyBookmarksSnapshot
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LegacyBookmarksDataSourceImpl @Inject constructor(
  private val bookmarksDBAdapter: BookmarksDBAdapter
) : LegacyBookmarksDataSource {
  override fun snapshot(): LegacyBookmarksSnapshot {
    return LegacyBookmarksSnapshot(
      tags = bookmarksDBAdapter.getTagsForMigration(),
      bookmarks = bookmarksDBAdapter.getBookmarks(SORT_DATE_ADDED),
      recentPages = bookmarksDBAdapter.getRecentPages()
    )
  }
}
