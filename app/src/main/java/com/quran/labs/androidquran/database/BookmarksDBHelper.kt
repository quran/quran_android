package com.quran.labs.androidquran.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import timber.log.Timber
import java.lang.Exception

class BookmarksDBHelper private constructor(context: Context) :
  SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  internal object BookmarksTable {
    const val TABLE_NAME = "bookmarks"
    const val ID = "_ID"
    const val SURA = "sura"
    const val AYAH = "ayah"
    const val PAGE = "page"
    const val ADDED_DATE = "added_date"
  }

  internal object TagsTable {
    const val TABLE_NAME = "tags"
    const val ID = "_ID"
    const val NAME = "name"
    const val ADDED_DATE = "added_date"
  }

  internal object BookmarkTagTable {
    const val TABLE_NAME = "bookmark_tag"
    const val ID = "_ID"
    const val BOOKMARK_ID = "bookmark_id"
    const val TAG_ID = "tag_id"
    const val ADDED_DATE = "added_date"
  }

  internal object LastPagesTable {
    const val TABLE_NAME = "last_pages"
    const val ID = "_ID"
    const val PAGE = "page"
    const val ADDED_DATE = "added_date"
  }

  companion object {
    private const val DB_NAME = "bookmarks.db"
    private const val DB_VERSION = 3

    const val QUERY_BOOKMARKS =
      "SELECT " + BookmarksTable.TABLE_NAME + "." + BookmarksTable.ID + ", " +
          BookmarksTable.TABLE_NAME + "." + BookmarksTable.SURA + ", " +
          BookmarksTable.TABLE_NAME + "." + BookmarksTable.AYAH + "," +
          BookmarksTable.TABLE_NAME + "." + BookmarksTable.PAGE + ", " +
          "strftime('%s', " + BookmarksTable.TABLE_NAME + "." + BookmarksTable.ADDED_DATE + ")" +
          ", " +
          BookmarkTagTable.TABLE_NAME + "." + BookmarkTagTable.TAG_ID +
          " FROM " +
          BookmarksTable.TABLE_NAME + " LEFT JOIN " + BookmarkTagTable.TABLE_NAME +
          " ON " + BookmarksTable.TABLE_NAME + "." + BookmarksTable.ID + " = " +
          BookmarkTagTable.TABLE_NAME + "." + BookmarkTagTable.BOOKMARK_ID

    private const val CREATE_BOOKMARKS_TABLE =
      " create table if not exists " + BookmarksTable.TABLE_NAME + " (" +
          BookmarksTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
          BookmarksTable.SURA + " INTEGER, " +
          BookmarksTable.AYAH + " INTEGER, " +
          BookmarksTable.PAGE + " INTEGER NOT NULL, " +
          BookmarksTable.ADDED_DATE +
          " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

    private const val CREATE_TAGS_TABLE =
      " create table if not exists " + TagsTable.TABLE_NAME + " (" +
          TagsTable.ID + " INTEGER PRIMARY KEY, " +
          TagsTable.NAME + " TEXT NOT NULL, " +
          TagsTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

    private const val CREATE_BOOKMARK_TAG_TABLE =
      " create table if not exists " + BookmarkTagTable.TABLE_NAME + " (" +
          BookmarkTagTable.ID + " INTEGER PRIMARY KEY, " +
          BookmarkTagTable.BOOKMARK_ID + " INTEGER NOT NULL, " +
          BookmarkTagTable.TAG_ID + " INTEGER NOT NULL, " +
          BookmarkTagTable.ADDED_DATE +
          " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

    private const val BOOKMARK_TAGS_INDEX =
      "create unique index if not exists " +
          BookmarkTagTable.TABLE_NAME + "_index on " +
          BookmarkTagTable.TABLE_NAME + "(" +
          BookmarkTagTable.BOOKMARK_ID + "," +
          BookmarkTagTable.TAG_ID + ");"

    private const val LAST_PAGES_TABLE =
      "create table if not exists " + LastPagesTable.TABLE_NAME + " (" +
          LastPagesTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
          LastPagesTable.PAGE + " INTEGER NOT NULL UNIQUE, " +
          LastPagesTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

    private var sInstance: BookmarksDBHelper? = null

    fun getInstance(context: Context): BookmarksDBHelper {
      if (sInstance == null) {
        sInstance = BookmarksDBHelper(context.applicationContext)
      }
      return sInstance as BookmarksDBHelper
    }
  }

  private val lastPage: Int

  init {
    val quranSettings = QuranSettings.getInstance(context)
    lastPage = quranSettings.lastPage
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(CREATE_BOOKMARKS_TABLE)
    db.execSQL(CREATE_TAGS_TABLE)
    db.execSQL(CREATE_BOOKMARK_TAG_TABLE)
    db.execSQL(BOOKMARK_TAGS_INDEX)
    db.execSQL(LAST_PAGES_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (newVersion <= oldVersion) {
      Timber.w("Can't downgrade from version %d to version %d", oldVersion, newVersion)
      return
    }
    Timber.i("Upgrading database from version %d to version %d", oldVersion, newVersion)
    if (oldVersion < 2) {
      upgradeToVer2(db)
    }

    if (oldVersion < 3) {
      upgradeToVer3(db)
    }
  }

  private fun upgradeToVer3(db: SQLiteDatabase) {
    db.execSQL(LAST_PAGES_TABLE)

    // the default lastPage is -1
    if (lastPage >= Constants.PAGES_FIRST) {
      db.execSQL("INSERT INTO last_pages(page) values(?)", arrayOf<Any>(lastPage))
    }
  }

  private fun upgradeToVer2(db: SQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS tags")
    db.execSQL("DROP TABLE IF EXISTS ayah_tag_map")
    db.execSQL(CREATE_BOOKMARKS_TABLE)
    db.execSQL(CREATE_TAGS_TABLE)
    db.execSQL(CREATE_BOOKMARK_TAG_TABLE)
    db.execSQL(BOOKMARK_TAGS_INDEX)
    copyOldBookmarks(db)
  }

  private fun copyOldBookmarks(db: SQLiteDatabase) {
    try {
      // Copy over ayah bookmarks
      db.execSQL("INSERT INTO bookmarks(_id, sura, ayah, page) " +
          "SELECT _id, sura, ayah, page FROM ayah_bookmarks WHERE " +
          "bookmarked = 1")

      // Copy over page bookmarks
      db.execSQL("INSERT INTO bookmarks(page) " +
          "SELECT _id from page_bookmarks where bookmarked = 1")
    } catch (e: Exception) {
      Timber.e(e, "Failed to copy old bookmarks")
    }
  }
}
