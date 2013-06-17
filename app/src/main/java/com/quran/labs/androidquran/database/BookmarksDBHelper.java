package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class BookmarksDBHelper extends SQLiteOpenHelper {

   private static final String TAG = "BookmarksDBHelper";
   
	private static final String DB_NAME = "bookmarks.db";
	private static final int DB_VERSION = 2;

   public static class BookmarksTable {
      public static final String TABLE_NAME = "bookmarks";
      public static final String ID = "_ID";
      public static final String SURA = "sura";
      public static final String AYAH = "ayah";
      public static final String PAGE = "page";
      public static final String ADDED_DATE = "added_date";
   }
	
   public static class TagsTable {
      public static final String TABLE_NAME = "tags";
      public static final String ID = "_ID";
      public static final String NAME = "name";
      public static final String ADDED_DATE = "added_date";
   }
   
   public static class BookmarkTagTable {
      public static final String TABLE_NAME = "bookmark_tag";
      public static final String ID = "_ID";
      public static final String BOOKMARK_ID = "bookmark_id";
      public static final String TAG_ID = "tag_id";
      public static final String ADDED_DATE = "added_date";
   }
   
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

   private static BookmarksDBHelper sInstance;

   public static BookmarksDBHelper getInstance(Context context){
      if (sInstance == null){
         sInstance = new BookmarksDBHelper(context.getApplicationContext());
      }
      return sInstance;
   }
	
	private BookmarksDBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_BOOKMARKS_TABLE);
		db.execSQL(CREATE_TAGS_TABLE);
		db.execSQL(CREATE_BOOKMARK_TAG_TABLE);
      db.execSQL(BOOKMARK_TAGS_INDEX);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	   if (newVersion <= oldVersion) {
	      Log.w(TAG, "Can't downgrade from version " +
                 oldVersion + " to version " + newVersion);
	      return;
	   }
      Log.i(TAG, "Upgrading database from version " +
              oldVersion + " to version " + newVersion);
	   if (oldVersion < 2) {
	      upgradeToVer2(db);
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
         Log.e(TAG, "Failed to copy old bookmarks", e);
      }
   }
}
