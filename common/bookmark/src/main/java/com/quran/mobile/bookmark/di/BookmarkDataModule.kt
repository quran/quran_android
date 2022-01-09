package com.quran.mobile.bookmark.di

import android.content.Context
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.BookmarksDatabase
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.AfterVersionWithDriver
import com.squareup.sqldelight.db.SqlDriver
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
class BookmarkDataModule {

  @Singleton
  @Provides
  fun provideBookmarksDatabase(context: Context, settings: Settings): BookmarksDatabase {
    val driver: SqlDriver = AndroidSqliteDriver(
      schema = BookmarksDatabase.Schema,
      context = context,
      name = "bookmarks.db",
      callback = AndroidSqliteDriver.Callback(
        BookmarksDatabase.Schema,
        AfterVersionWithDriver(2) {
          val lastPage = runBlocking { settings.lastPage() }
          if (lastPage > -1) {
            it.execute(null, "INSERT INTO last_pages(page) values($lastPage)", 0)
          }
        }
      ),
    )
    return BookmarksDatabase(driver)
  }
}
