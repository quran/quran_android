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
        AfterVersionWithDriver(1) {
          try {
            // Copy over ayah bookmarks
            it.execute(
              identifier = null,
              sql = "INSERT INTO bookmarks(_id, sura, ayah, page) " +
                  "SELECT _id, sura, ayah, page FROM ayah_bookmarks WHERE " +
                  "bookmarked = 1",
              parameters = 0
            )

            // Copy over page bookmarks
            it.execute(
              identifier = null,
              sql = "INSERT INTO bookmarks(page) " +
                  "SELECT _id from page_bookmarks where bookmarked = 1",
              parameters = 0
            )

            // Drop old tables
            it.execute(null, "DROP TABLE IF EXISTS ayah_bookmarks", 0)
            it.execute(null, "DROP TABLE IF EXISTS page_bookmarks", 0)
          } catch (e: Exception) {
          }
        },
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
