package com.quran.labs.androidquran.database;

import java.util.ArrayList;
import java.util.List;

import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.AyahTable;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.PageTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class BookmarksDatabaseHandler {

	private SQLiteDatabase db;
	private BookmarksDatabaseHelper dbHelper;
	
	public BookmarksDatabaseHandler(Context context) {
		this.dbHelper = new BookmarksDatabaseHelper(context);
	}

	// TODO Add error handling for SQLExceptions and such..

	public void open() throws SQLException {
		db = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}

	private Cursor findAyah(int page, int sura, int ayah, String[] columns) {
		return db.query(AyahTable.TABLE_NAME, columns,
				AyahTable.PAGE + "=" + page + " and " + AyahTable.SURA + "=" + sura + 
				" and " + AyahTable.AYAH + "=" + ayah, null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
	}
	
	public boolean toggleAyahBookmark(int page, int sura, int ayah) {
		Cursor cursor = findAyah(page, sura, ayah, 
				new String[] {AyahTable.ID, AyahTable.BOOKMARKED});
		if (!cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(AyahTable.PAGE, page);
			values.put(AyahTable.SURA, sura);
			values.put(AyahTable.AYAH, ayah);
			values.put(AyahTable.BOOKMARKED, 1);
			db.insert(AyahTable.TABLE_NAME, null, values);
			return true;
		} else {
			int id = cursor.getInt(0);
			boolean curValue = (cursor.getInt(1) != 0);
			ContentValues values = new ContentValues();
			values.put(AyahTable.BOOKMARKED, !curValue);
			db.update(AyahTable.TABLE_NAME, values, AyahTable.ID+"="+id, null);
			return !curValue;
		}
	}
	
	public boolean isAyahBookmarked(int page, int sura, int ayah) {
		Cursor cursor = findAyah(page, sura, ayah, new String[] {AyahTable.BOOKMARKED});
		if (!cursor.moveToFirst())
			return false;
		else
			return cursor.getInt(0) == 1;
	}
	
	public List<Integer[]> getAyahBookmarks() {
		Cursor cursor = db.query(AyahTable.TABLE_NAME, 
				new String[] {AyahTable.PAGE, AyahTable.SURA, AyahTable.AYAH},
				AyahTable.BOOKMARKED + "=1", null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
		List<Integer[]> ayahBookmarks = new ArrayList<Integer[]>();
		while(cursor.moveToNext()) {
			ayahBookmarks.add(new Integer[] {cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)});
		}
		return ayahBookmarks;
	}
	
	private Cursor findPage(int page) {
		return db.query(PageTable.TABLE_NAME, new String[] {PageTable.ID, PageTable.PAGE},
				PageTable.PAGE + "=" + page, null, null, null, PageTable.PAGE);
	}
	
	public boolean togglePageBookmark(int page) {
		Cursor cursor = findPage(page);
		if (!cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(PageTable.PAGE, page);
			db.insert(PageTable.TABLE_NAME, null, values);
			return true;
		} else {
			int id = cursor.getInt(0);
			db.delete(PageTable.TABLE_NAME, PageTable.ID+"="+id, null);
			return false;
		}
	}
	
	public List<Integer> getPageBookmarks() {
		Cursor cursor = db.query(PageTable.TABLE_NAME, new String[] {PageTable.PAGE},
				null, null, null, null, PageTable.PAGE);
		List<Integer> pageBookmarks = new ArrayList<Integer>();
		while(cursor.moveToNext()) {
			pageBookmarks.add(cursor.getInt(0));
		}
		return pageBookmarks;
	}
}
