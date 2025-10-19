package com.quran.mobile.bookmark.di

import android.content.Context
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.Bookmarks
import com.quran.mobile.bookmark.Last_pages
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking

@BindingContainer
@ContributesTo(AppScope::class)
class BookmarkDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideBookmarksDatabase(@ApplicationContext context: Context, settings: Settings): BookmarksDatabase {
    val driver: SqlDriver = AndroidSqliteDriver(
      schema = BookmarksDatabase.Schema,
      context = context,
      name = "bookmarks.db",
      callback = AndroidSqliteDriver.Callback(
        BookmarksDatabase.Schema,
        AfterVersion(2) {
          val lastPage = runBlocking { settings.lastPage() }
          if (lastPage > -1) {
            it.execute(null, "INSERT INTO last_pages(page) values($lastPage)", 0)
          }
        }
      ),
    )
    return BookmarksDatabase(
      driver,
      Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
      Last_pages.Adapter(IntColumnAdapter)
    )
  }
}
