package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class BookmarksDBHelper extends SQLiteOpenHelper {

	private static final String DB_NAME = "bookmarks.db";
	private static final int DB_VERSION = 1;
	
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
	
	static class TagsTable {
		static final String TABLE_NAME = "tags";
		static final String ID = "_id";
		static final String NAME = "name";
		static final String DESCRIPTION = "description";
		static final String COLOR = "color";
	}
	
	private static final String CREATE_TAGS_TABLE=
			" create table " + TagsTable.TABLE_NAME + " (" + 
					TagsTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					TagsTable.NAME + " TEXT NOT NULL, " +
					TagsTable.DESCRIPTION + " TEXT, " +
					TagsTable.COLOR + " INTEGER);";
	
	static class AyahTagMapTable {
		static final String TABLE_NAME = "ayah_tag_map";
		static final String ID = "_id";
		static final String AYAH_ID = "ayah_id";
		static final String TAG_ID = "tag_id";
	}
	
	private static final String CREATE_AYAH_TAG_MAP_TABLE=
			" create table " + AyahTagMapTable.TABLE_NAME + " (" + 
					AyahTagMapTable.ID + " INTEGER PRIMARY KEY, " +
					AyahTagMapTable.AYAH_ID + " INTEGER NOT NULL, " +
					AyahTagMapTable.TAG_ID + " INTEGER NOT NULL);";
	
	public BookmarksDBHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_AYAH_TABLE);
		db.execSQL(CREATE_PAGE_TABLE);
		db.execSQL(CREATE_TAGS_TABLE);
		db.execSQL(CREATE_AYAH_TAG_MAP_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// To be filled on DB_VERSION increment
	}

}
