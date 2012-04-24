package com.quran.labs.androidquran.database;

import java.util.ArrayList;
import java.util.List;

import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.AyahTable;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.PageTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class BookmarksDatabaseHandler {

	private SQLiteDatabase mDb;
	private BookmarksDatabaseHelper mDbHelper;
	
	public BookmarksDatabaseHandler(Context context) {
		this.mDbHelper = new BookmarksDatabaseHelper(context);
	}

	// TODO Add error handling for SQLExceptions and such..

	public void open() throws SQLException {
		mDb = mDbHelper.getWritableDatabase();
	}

	public void close() {
		mDbHelper.close();
	}

	private Cursor findAyah(int sura, int ayah, String[] columns) {
		return findAyah(QuranInfo.getAyahId(sura, ayah), columns);
	}
	
	private Cursor findAyah(int ayahId, String[] columns) {
		return mDb.query(AyahTable.TABLE_NAME, columns, AyahTable.ID + "=" + ayahId,
				null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
	}
	
	private ContentValues createAyahValues(
			int ayahId, int page, int sura, int ayah, int bookmarked) {
		ContentValues values = new ContentValues();
		values.put(AyahTable.ID, ayahId);
		values.put(AyahTable.PAGE, page);
		values.put(AyahTable.SURA, sura);
		values.put(AyahTable.AYAH, ayah);
		values.put(AyahTable.BOOKMARKED, bookmarked);
		return values;
	}
	
	// Returns new isBookmarked value
	public boolean toggleAyahBookmark(int page, int sura, int ayah) {
		int ayahId = QuranInfo.getAyahId(sura, ayah);
		Cursor cursor = findAyah(ayahId, new String[] {AyahTable.BOOKMARKED});
		if (cursor.moveToFirst()) {
			boolean isBookmarked = (cursor.getInt(0) != 0);
			ContentValues values = new ContentValues();
			values.put(AyahTable.BOOKMARKED, !isBookmarked);
			mDb.update(AyahTable.TABLE_NAME, values, AyahTable.ID+"="+ayahId, null);
			return !isBookmarked;
		} else {
			ContentValues values = createAyahValues(ayahId, page, sura, ayah, 1);
			mDb.insert(AyahTable.TABLE_NAME, null, values);
			return true;
		}
	}
	
	public boolean isAyahBookmarked(int sura, int ayah) {
		Cursor cursor = findAyah(sura, ayah, new String[] {AyahTable.BOOKMARKED});
		return (cursor.moveToFirst() && cursor.getInt(0) == 1);
	}
	
	public List<Integer[]> getAyahBookmarks() {
		return getAyahBookmarks(null);
	}
	
	public List<Integer[]> getAyahBookmarks(Integer page) {
		String query = AyahTable.BOOKMARKED + "=1";
		if(page != null)
			query += " and " + AyahTable.PAGE + "=" + page;
		Cursor cursor = mDb.query(AyahTable.TABLE_NAME,
				new String[] {AyahTable.PAGE, AyahTable.SURA, AyahTable.AYAH},
				query, null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
		List<Integer[]> ayahBookmarks = new ArrayList<Integer[]>();
		while(cursor.moveToNext()) {
			ayahBookmarks.add(new Integer[] {cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)});
		}
		return ayahBookmarks;
	}
	
	// If no notes, returns empty string
	public String getAyahNotes(int sura, int ayah) {
		Cursor cursor = findAyah(sura, ayah, new String[] {AyahTable.NOTES});
		if (!cursor.moveToFirst() || cursor.isNull(0))
			return "";
		return cursor.getString(0);
	}
	
	public void saveAyahNotes(int page, int sura, int ayah, String notes) {
		int ayahId = QuranInfo.getAyahId(sura, ayah);
		Cursor cursor = findAyah(ayahId, new String[] {AyahTable.BOOKMARKED});
		if (cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(AyahTable.NOTES, notes);
			mDb.update(AyahTable.TABLE_NAME, values, AyahTable.ID+"="+ayahId, null);
		} else {
			ContentValues values = createAyahValues(ayahId, page, sura, ayah, 0);
			values.put(AyahTable.NOTES, notes);
			mDb.insert(AyahTable.TABLE_NAME, null, values);
		}
	}
	
	private Cursor findPage(int page) {
		return mDb.query(PageTable.TABLE_NAME, new String[] {PageTable.BOOKMARKED},
				PageTable.ID + "=" + page, null, null, null, PageTable.ID);
	}
	
	public boolean togglePageBookmark(int page) {
		Cursor cursor = findPage(page);
		if (!cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(PageTable.ID, page);
			values.put(PageTable.BOOKMARKED, 1);
			mDb.insert(PageTable.TABLE_NAME, null, values);
			return true;
		} else {
			boolean isBookmarked = (cursor.getInt(0) != 0);
			ContentValues values = new ContentValues();
			values.put(PageTable.BOOKMARKED, !isBookmarked);
			mDb.update(PageTable.TABLE_NAME, values, PageTable.ID+"="+page, null);
			return !isBookmarked;
		}
	}
	
	public List<Integer> getPageBookmarks() {
		Cursor cursor = mDb.query(PageTable.TABLE_NAME, new String[] {PageTable.ID},
				PageTable.BOOKMARKED + "=1", null, null, null, PageTable.ID);
		List<Integer> pageBookmarks = new ArrayList<Integer>();
		while(cursor.moveToNext()) {
			pageBookmarks.add(cursor.getInt(0));
		}
		return pageBookmarks;
	}
}
