package com.quran.mobile.bookmark.db

import android.content.Context
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import com.quran.shared.persistence.MobileSyncDatabase
import com.quran.shared.persistence.MobileSyncSchema
import java.util.Properties

/**
 * A fresh in-memory mobile-sync database, opened the way the Android driver opens `user_data.db`:
 * [MobileSyncSchema] (which seeds Favorites) with foreign keys on.
 */
internal fun inMemoryMobileSyncDatabase(): MobileSyncDatabase {
  val driver = JdbcSqliteDriver(
    JdbcSqliteDriver.IN_MEMORY,
    Properties().apply { put("foreign_keys", "true") },
    MobileSyncSchema
  )
  return MobileSyncDatabase(driver)
}

/**
 * A fresh in-memory bookmarks database (bookmarks.db), opened through Robolectric's SQLite as the
 * Android driver opens it (JDBC drivers don't always register in Robolectric's class loaders).
 */
internal fun inMemoryBookmarksDatabase(context: Context): BookmarksDatabase {
  val driver = AndroidSqliteDriver(BookmarksDatabase.Schema, context, name = null)
  return BookmarksDatabase(
    driver,
    Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
    Last_pages.Adapter(IntColumnAdapter)
  )
}
