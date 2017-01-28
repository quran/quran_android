package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.QuranSettings;

import timber.log.Timber;

class BookmarksDBHelper extends SQLiteOpenHelper {

  private static final String DB_NAME = "bookmarks.db";
  private static final int DB_VERSION = 3;

  static class BookmarksTable {
    static final String TABLE_NAME = "bookmarks";
    static final String ID = "_ID";
    static final String SURA = "sura";
    static final String AYAH = "ayah";
    static final String PAGE = "page";
    static final String ADDED_DATE = "added_date";
  }

  static class TagsTable {
    static final String TABLE_NAME = "tags";
    static final String ID = "_ID";
    static final String NAME = "name";
    static final String ADDED_DATE = "added_date";
  }

  static class BookmarkTagTable {
    static final String TABLE_NAME = "bookmark_tag";
    static final String ID = "_ID";
    static final String BOOKMARK_ID = "bookmark_id";
    static final String TAG_ID = "tag_id";
    static final String ADDED_DATE = "added_date";
  }

  static class LastPagesTable {
    static final String TABLE_NAME = "last_pages";
    static final String ID = "_ID";
    static final String PAGE = "page";
    static final String ADDED_DATE = "added_date";
  }

  static final String QUERY_BOOKMARKS =
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
          BookmarkTagTable.TABLE_NAME + "." + BookmarkTagTable.BOOKMARK_ID;

  private static final String CREATE_BOOKMARKS_TABLE =
      " create table if not exists " + BookmarksTable.TABLE_NAME + " (" +
          BookmarksTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
          BookmarksTable.SURA + " INTEGER, " +
          BookmarksTable.AYAH + " INTEGER, " +
          BookmarksTable.PAGE + " INTEGER NOT NULL, " +
          BookmarksTable.ADDED_DATE +
          " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";

  private static final String CREATE_TAGS_TABLE =
      " create table if not exists " + TagsTable.TABLE_NAME + " (" +
          TagsTable.ID + " INTEGER PRIMARY KEY, " +
          TagsTable.NAME + " TEXT NOT NULL, " +
          TagsTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";

  private static final String CREATE_BOOKMARK_TAG_TABLE =
      " create table if not exists " + BookmarkTagTable.TABLE_NAME + " (" +
          BookmarkTagTable.ID + " INTEGER PRIMARY KEY, " +
          BookmarkTagTable.BOOKMARK_ID + " INTEGER NOT NULL, " +
          BookmarkTagTable.TAG_ID + " INTEGER NOT NULL, " +
          BookmarkTagTable.ADDED_DATE +
          " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";

  private static final String BOOKMARK_TAGS_INDEX =
      "create unique index if not exists " +
          BookmarkTagTable.TABLE_NAME + "_index on " +
          BookmarkTagTable.TABLE_NAME + "(" +
          BookmarkTagTable.BOOKMARK_ID + "," +
          BookmarkTagTable.TAG_ID + ");";

  private static final String LAST_PAGES_TABLE =
      "create table if not exists " + LastPagesTable.TABLE_NAME + " (" +
          LastPagesTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
          LastPagesTable.PAGE + " INTEGER NOT NULL UNIQUE, " +
          LastPagesTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";

  private static BookmarksDBHelper sInstance;

  public static BookmarksDBHelper getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new BookmarksDBHelper(context.getApplicationContext());
    }
    return sInstance;
  }

  private final int lastPage;

  private BookmarksDBHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    QuranSettings quranSettings = QuranSettings.getInstance(context);
    lastPage = quranSettings.getLastPage();
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(CREATE_BOOKMARKS_TABLE);
    db.execSQL(CREATE_TAGS_TABLE);
    db.execSQL(CREATE_BOOKMARK_TAG_TABLE);
    db.execSQL(BOOKMARK_TAGS_INDEX);
    db.execSQL(LAST_PAGES_TABLE);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (newVersion <= oldVersion) {
      Timber.w("Can't downgrade from version %d to version %d", oldVersion, newVersion);
      return;
    }
    Timber.i("Upgrading database from version %d to version %d", oldVersion, newVersion);
    if (oldVersion < 2) {
      upgradeToVer2(db);
    }

    if (oldVersion < 3) {
      upgradeToVer3(db);
    }
  }

  private void upgradeToVer3(SQLiteDatabase db) {
    db.execSQL(LAST_PAGES_TABLE);
    if (this.lastPage >= Constants.PAGES_FIRST && this.lastPage <= Constants.PAGES_LAST) {
      db.execSQL("INSERT INTO last_pages(page) values(?)", new Object[] { lastPage });
    }
  }

  private void upgradeToVer2(SQLiteDatabase db) {
    db.execSQL("DROP TABLE IF EXISTS tags");
    db.execSQL("DROP TABLE IF EXISTS ayah_tag_map");
    db.execSQL(CREATE_BOOKMARKS_TABLE);
    db.execSQL(CREATE_TAGS_TABLE);
    db.execSQL(CREATE_BOOKMARK_TAG_TABLE);
    db.execSQL(BOOKMARK_TAGS_INDEX);
    copyOldBookmarks(db);
  }

  private void copyOldBookmarks(SQLiteDatabase db) {
    try {
      // Copy over ayah bookmarks
      db.execSQL("INSERT INTO bookmarks(_id, sura, ayah, page) " +
          "SELECT _id, sura, ayah, page FROM ayah_bookmarks WHERE " +
          "bookmarked = 1");

      // Copy over page bookmarks
      db.execSQL("INSERT INTO bookmarks(page) " +
          "SELECT _id from page_bookmarks where bookmarked = 1");
    } catch (Exception e) {
      Timber.e(e, "Failed to copy old bookmarks");
    }
  }
}
