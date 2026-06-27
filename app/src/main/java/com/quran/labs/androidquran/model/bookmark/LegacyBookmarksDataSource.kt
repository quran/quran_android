package com.quran.labs.androidquran.model.bookmark

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.LegacyBookmarksSnapshot
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Suppress("DEPRECATION")
class LegacyBookmarksDataSourceImpl @Inject constructor(
  private val bookmarksDBAdapter: BookmarksDBAdapter
) : LegacyBookmarksDataSource {
  override fun snapshot(): LegacyBookmarksSnapshot {
    return LegacyBookmarksSnapshot(
      tags = bookmarksDBAdapter.getTagsForMigration(),
      bookmarks = bookmarksDBAdapter.getBookmarksForMigration(),
      recentPages = bookmarksDBAdapter.getRecentPages()
    )
  }
}
