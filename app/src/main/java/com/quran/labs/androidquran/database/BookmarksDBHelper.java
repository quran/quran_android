package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.quran.labs.androidquran.R;

class BookmarksDBHelper extends SQLiteOpenHelper {

   private static final String TAG = "BookmarksDBHelper";
   
	private static final String DB_NAME = "bookmarks.db";
	private static final int DB_VERSION = 2;
	
   private Context cx;

   public static class BookmarksTable {
      public static final String TABLE_NAME = "bookmarks";
      public static final String ID = "_ID";
      public static final String SURA = "sura";
      public static final String AYAH = "ayah";
      public static final String PAGE = "page";
//      public static final String CATEGORY_ID = "category_id";
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
   
	private static final String CREATE_BOOKMARKS_TABLE=
			" create table " + BookmarksTable.TABLE_NAME + " (" + 
					BookmarksTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + // XXX Auto-increment necessary?
					BookmarksTable.SURA + " INTEGER, " +
               BookmarksTable.AYAH + " INTEGER, " +
               BookmarksTable.PAGE + " INTEGER NOT NULL, " +
//               BookmarksTable.CATEGORY_ID + " INTEGER, " +
               BookmarksTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
	
	private static final String CREATE_TAGS_TABLE=
	      " create table " + TagsTable.TABLE_NAME + " (" + 
	            TagsTable.ID + " INTEGER PRIMARY KEY, " +
	            TagsTable.NAME + " TEXT NOT NULL, " +
	            TagsTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
	
	private static final String CREATE_BOOKMARK_TAG_TABLE=
	      " create table " + BookmarkTagTable.TABLE_NAME + " (" + 
	            BookmarkTagTable.ID + " INTEGER PRIMARY KEY, " +
	            BookmarkTagTable.BOOKMARK_ID + " INTEGER NOT NULL, " +
	            BookmarkTagTable.TAG_ID + " INTEGER NOT NULL, " +
	            BookmarkTagTable.ADDED_DATE + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
/*
   public static class BookmarkCategoriesTable {
      public static final String TABLE_NAME = "bookmark_categories";
      public static final String ID = "_id";
      public static final String NAME = "name";
      public static final String DESCRIPTION = "description";
	}
	
	private static final String CREATE_BOOKMARK_CATEGORIES_TABLE=
			" create table " + BookmarkCategoriesTable.TABLE_NAME + " (" +
                 BookmarkCategoriesTable.ID + " INTEGER PRIMARY KEY, " +
                 BookmarkCategoriesTable.NAME + " VARCHAR NOT NULL, " +
                 BookmarkCategoriesTable.DESCRIPTION + " VARCHAR);";
*/	
	public BookmarksDBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
		this.cx = context;
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_BOOKMARKS_TABLE);
		db.execSQL(CREATE_TAGS_TABLE);
		db.execSQL(CREATE_BOOKMARK_TAG_TABLE);
//		db.execSQL(CREATE_BOOKMARK_CATEGORIES_TABLE);
//      createSampleBookmarks(db);
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
//      db.execSQL(CREATE_BOOKMARK_CATEGORIES_TABLE);
//      createSampleBookmarks(db);
      copyOldBookmarks(db);
   }
/*   
   private void createSampleBookmarks(SQLiteDatabase db) {
      try {
         db.execSQL("INSERT INTO bookmark_categories VALUES (1, ?, null)",
               new String[] {cx.getString(R.string.sample_bookmark_reading)});
         db.execSQL("INSERT INTO bookmark_categories VALUES (2, ?, null)",
               new String[] {cx.getString(R.string.sample_bookmark_duas)});
         final String s = "INSERT INTO bookmarks(_id, sura, ayah, " +
            "page, category_id)";
         db.execSQL(s + " VALUES(NULL, NULL, NULL, 1, 1)"); // Readings (pg 1)
         db.execSQL(s + " VALUES(NULL, 3, 16, 52, 2)");     // Duas (3:16)
         db.execSQL(s + " VALUES(NULL, 25, 65, 365, 2)");   // Duas (25:65)
      } catch (Exception e) {
         Log.e(TAG, "Failed to create sample bookmarks", e);
      }
   }
*/   
   private void copyOldBookmarks(SQLiteDatabase db) {
      try {
         // Copy over ayah bookmarks
         db.execSQL("INSERT INTO bookmarks(_id, sura, ayah, page) " + // XXX Should we insert _id?
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
