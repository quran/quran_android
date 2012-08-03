package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;

class BookmarksDBHelper extends SQLiteOpenHelper {

   private static final String TAG = "BookmarksDBHelper";
   
	private static final String DB_NAME = "bookmarks.db";
	private static final int DB_VERSION = 2;
	
   private Context cx;
   /*
	static class AyahTable {
		static final String TABLE_NAME = "ayah_bookmarks";
		static final String ID = "_id";
		static final String PAGE = "page";
		static final String SURA = "sura";
		static final String AYAH = "ayah";
		static final String BOOKMARKED = "bookmarked";
		static final String NOTES = "notes";
	}
	
	private static final String CREATE_AYAH_TABLE =
			" create table " + AyahTable.TABLE_NAME + " (" + 
					AyahTable.ID + " INTEGER PRIMARY KEY, " +
					AyahTable.PAGE + " INTEGER NOT NULL, " +
					AyahTable.SURA + " INTEGER NOT NULL, " +
					AyahTable.AYAH + " INTEGER NOT NULL, " +
					AyahTable.BOOKMARKED + " INTEGER NOT NULL, " +
					AyahTable.NOTES + " TEXT);";
	
	static class PageTable {
		static final String TABLE_NAME = "page_bookmarks";
		static final String ID = "_id";
		static final String BOOKMARKED = "bookmarked";
	}
	
	private static final String CREATE_PAGE_TABLE =
			" create table " + PageTable.TABLE_NAME + " (" + 
					PageTable.ID + " INTEGER PRIMARY KEY, " +
					PageTable.BOOKMARKED + " INTEGER NOT NULL);";
	*/
	static class BookmarksTable {
		static final String TABLE_NAME = "bookmarks";
		static final String ID = "_id";
		static final String NAME = "name";
		static final String TYPE = "type";
		static final String DESCRIPTION = "description";
      static final int TYPE_BOOKMARK = 0;
      static final int TYPE_COLLECTION = 1;
	}
	
	private static final String CREATE_BOOKMARKS_TABLE=
			" create table " + BookmarksTable.TABLE_NAME + " (" + 
					BookmarksTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					BookmarksTable.TYPE + " INTEGER NOT NULL, " +
					BookmarksTable.NAME + " TEXT NOT NULL, " +
					BookmarksTable.DESCRIPTION + " TEXT);";
	
	static class BookmarkMapTable {
		static final String TABLE_NAME = "bookmark_map";
		static final String ID = "_id";
		static final String BOOKMARK_ID = "bookmark_id";
		static final String AYAH_ID = "ayah_id";
		static final String TYPE = "type";
		static final int TYPE_AYAH = 0;
		static final int TYPE_PAGE = 1;
	}
	
	private static final String CREATE_BOOKMARK_MAP_TABLE=
			" create table " + BookmarkMapTable.TABLE_NAME + " (" + 
					BookmarkMapTable.ID + " INTEGER PRIMARY KEY, " +
					BookmarkMapTable.BOOKMARK_ID + " INTEGER NOT NULL, " +
					BookmarkMapTable.AYAH_ID + " INTEGER NOT NULL, " +
					BookmarkMapTable.TYPE + " INTEGER NOT NULL DEFAULT 0);";
	
	public BookmarksDBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
		this.cx = context;
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
//		db.execSQL(CREATE_AYAH_TABLE);
//		db.execSQL(CREATE_PAGE_TABLE);
		db.execSQL(CREATE_BOOKMARKS_TABLE);
		db.execSQL(CREATE_BOOKMARK_MAP_TABLE);
      createSampleBookmarks(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.i(TAG, "Upgrading database from version "+oldVersion+" to version "+newVersion);
	   if (oldVersion < 2) {
	      upgradeToVer2(db);
	   }
	}

   private void upgradeToVer2(SQLiteDatabase db) {
      db.execSQL("DROP TABLE IF EXISTS tags");
      db.execSQL("DROP TABLE IF EXISTS ayah_tag_map");
      onCreate(db);
      copyOldBookmarks(db);
   }
   
   private void createSampleBookmarks(SQLiteDatabase db) {
      try {
         db.execSQL("INSERT INTO bookmarks VALUES (1, 0, ?, null)",
               new String[] {cx.getString(R.string.sample_bookmark_reading)});
         db.execSQL("INSERT INTO bookmarks VALUES (2, 1, ?, null)",
               new String[] {cx.getString(R.string.sample_bookmark_duas)});
         final String s = "INSERT INTO bookmark_map (bookmark_id, ayah_id, type)";
         db.execSQL(s+" VALUES (1, 1, 1)"); // Daily Reading (pg 1)
         db.execSQL(s+" VALUES (2, 301, 0)"); // Quranic Duas (ayah 3:16)
         db.execSQL(s+" VALUES (2, 2920, 0)"); // Quranic Duas (ayah 25:65)
      } catch (Exception e) {
         Log.e(TAG, "Failed to create sample bookmarks", e);
      }
   }
   
   private void copyOldBookmarks(SQLiteDatabase db) {
      try {
         db.execSQL("INSERT INTO bookmarks VALUES (3, 1, ?, null)",
               new String[] {cx.getString(R.string.sample_bookmark_uncategorized)});
         // Copy over ayah bookmarks
         db.execSQL("INSERT INTO bookmark_map (bookmark_id, ayah_id, type) " +
         		"SELECT 3, _id, 0 FROM ayah_bookmarks WHERE bookmarked=1");
         // Copy over page bookmarks
         Cursor c = db.rawQuery("SELECT _id FROM page_bookmarks WHERE bookmarked=1", null);
         while (c.moveToNext()) {
            int ayahId = QuranInfo.getAyahId(c.getInt(0));
            db.execSQL("INSERT INTO bookmark_map (bookmark_id, ayah_id, type) " +
            		"VALUES (3, "+ayahId+", 1)");
         }
         c.close();
      } catch (Exception e) {
         Log.e(TAG, "Failed to copy old bookmarks", e);
      }
   }

}
