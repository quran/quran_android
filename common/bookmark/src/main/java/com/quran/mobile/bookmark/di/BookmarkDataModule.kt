package com.quran.mobile.bookmark.di

import android.content.Context
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.util.AfterMigrationVersion
import com.quran.mobile.bookmark.util.MigrationSQLiteOpenHelperCallback
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.sqldelight.android.AndroidSqliteDriver
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
      callback = MigrationSQLiteOpenHelperCallback(
        BookmarksDatabase.Schema,
        AfterMigrationVersion(1) {
          try {
            // Copy over ayah bookmarks
            it.execSQL(
              "INSERT INTO bookmarks(_id, sura, ayah, page) " +
                  "SELECT _id, sura, ayah, page FROM ayah_bookmarks WHERE " +
                  "bookmarked = 1"
            )

            // Copy over page bookmarks
            it.execSQL(
              "INSERT INTO bookmarks(page) " +
                  "SELECT _id from page_bookmarks where bookmarked = 1"
            )

            // Drop old tables
            it.execSQL("DROP TABLE IF EXISTS ayah_bookmarks")
            it.execSQL("DROP TABLE IF EXISTS page_bookmarks")
          } catch (e: Exception) {
          }
        },
        AfterMigrationVersion(2) {
          val lastPage = runBlocking { settings.lastPage() }
          if (lastPage > -1) {
            it.execSQL("INSERT INTO last_pages(page) values(?)", arrayOf<Any>(lastPage))
          }
        }
      ),
    )
    return BookmarksDatabase(driver)
  }
}
