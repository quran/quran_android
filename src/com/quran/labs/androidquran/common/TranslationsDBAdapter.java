package com.quran.labs.androidquran.common;

import java.util.ArrayList;

import com.quran.labs.androidquran.util.QuranSettings;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TranslationsDBAdapter {
	
	private static final int DATABASE_VERSION = 2;
	public static final String TABLE_NAME = "Translations";

	public static final String COLUMN_ID = "id";
	public static final String COLUMN_DISPLAY_NAME = "displayName";
	public static final String COLUMN_FILE_NAME = "fileName";
	public static final String COLUMN_DOWNLOAD_TYPE = "downloadType";
	
	private final Context context;
	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;
	
	private static final String DATABASE_CREATE = 
		"CREATE TABLE " + TABLE_NAME + "(" +
		COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY, " +
		COLUMN_DISPLAY_NAME + " TEXT NOT NULL, " +
		COLUMN_DOWNLOAD_TYPE + " TEXT NOT NULL, " +
		COLUMN_FILE_NAME + " TEXT NOT NULL)";
	 
	public TranslationsDBAdapter(Context ctx) {
        this.context = ctx;
        DBHelper = new DatabaseHelper(context);
    }

	public TranslationsDBAdapter open() throws SQLException {
        db = DBHelper.getWritableDatabase();
        return this;
    }
	
	public long insert(DownloadItem translation) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(COLUMN_ID, translation.getId());
		initialValues.put(COLUMN_DISPLAY_NAME, translation.getDisplayName());
		initialValues.put(COLUMN_FILE_NAME, translation.getFileName());
		initialValues.put(COLUMN_DOWNLOAD_TYPE, translation.getDownloadType());
		return db.insert(TABLE_NAME, null, initialValues);
	}
	
	public void save(DownloadItem[] downloadItems) {
		open();
		for (int i = 0; i < downloadItems.length; i++) {
			insert(downloadItems[i]);
		}
		Log.i("Translations DB", "Inserted " + downloadItems.length + " records");
		close();
	}
	
	public TranslationItem[] getAllTranslations() {
		return getTranslations(COLUMN_DOWNLOAD_TYPE + " = ?", new String[]{DownloadItem.DOWNLOAD_TYPE_TRANSLATION});
	}
	
	public TranslationItem[] getAll() {
		return getTranslations(null, null);
	}
	
	public TranslationItem getQuranScript() {
		TranslationItem[] items = getTranslations(COLUMN_DOWNLOAD_TYPE + " = ?", new String[]{DownloadItem.DOWNLOAD_TYPE_SCRIPT}); 
		return items.length > 0 && items[0].isDownloaded() ? items[0] : null;
	}
	
	public TranslationItem[] getAvailableTranslations() {
		return getAvailableTranslations(false);
	}
	
	public TranslationItem findTranslation(String fileName) {
		if (fileName == null) 
			return null;
		TranslationItem[] items = getTranslations(COLUMN_FILE_NAME + " = ?", new String[]{fileName}); 
		return items.length > 0 && items[0].isDownloaded() ? items[0] : null;
	}
	
	public TranslationItem getActiveTranslation() {
		return findTranslation(QuranSettings.getInstance().getActiveTranslation());
	}
	
	public TranslationItem[] getAvailableTranslations(boolean includeScript) {
		ArrayList<TranslationItem> result = new ArrayList<TranslationItem>();
		TranslationItem[] items = includeScript ? getAll() : getAllTranslations();
		for (int i = 0; i < items.length; i++) {
			if (items[i].isDownloaded())
				result.add(items[i]);
		}
		return (TranslationItem[])result.toArray(new TranslationItem[result.size()]);		
	}
	
	private TranslationItem[] getTranslations(String selection, String[] selectionArgs) {
		TranslationItem[] results = null;
		open();
		Cursor cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, COLUMN_DISPLAY_NAME);
		results = loadTranslations(cursor);
		cursor.close();
		close();
		return results;
	}
	
	private TranslationItem[] loadTranslations(Cursor cursor) {
		TranslationItem[] results = null;
		results = new TranslationItem[cursor.getCount()];
		int i = 0;
		while (cursor.moveToNext()) {
			TranslationItem item = new TranslationItem();
			item.setId(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)));
			item.setDisplayName(cursor.getString(cursor.getColumnIndex(COLUMN_DISPLAY_NAME)));
			item.setFileName(cursor.getString(cursor.getColumnIndex(COLUMN_FILE_NAME)));
			results[i++] = item;
		}
		return results;
	}
	
	public boolean isDBEmpty(){
		open();
		boolean result = false;
		Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);
		result = cursor.moveToNext();
		cursor.close();
		close();
		return !result;
	}
		
	public int deleteAllRecords() {
		open();
		try {		
			return db.delete(TABLE_NAME, null, null);
		} catch (Exception e) {
			return 0;
		} finally {
			close();
		}
	}
		    
    public void close() {
        DBHelper.close();
    }  
		
	private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_CREATE, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			
		}
    }
}
