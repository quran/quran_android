package com.quran.labs.androidquran.data;

import java.io.File;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.quran.labs.androidquran.util.QuranUtils;


public class DatabaseHandler {
	
	private SQLiteDatabase database = null;
	public static String COL_SURA = "sura";
	public static String COL_AYAH = "ayah";
	public static String COL_TEXT = "text";
	public static String VERSE_TABLE = "verses";
	public static String AR_SEARCH_TABLE = "search";
	public static String TRANSLITERATION_TABLE = "transliteration";
	
	public DatabaseHandler(String databaseName) throws SQLException {
		String base = QuranUtils.getQuranDatabaseDirectory();
		if (base == null) return;
		String path = base + File.separator + databaseName;
		database = SQLiteDatabase.openDatabase(path, null,
				SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}
	
	public boolean validDatabase(){
		return (database == null)? false : database.isOpen();
	}
	
	public Cursor getVerses(int sura, int minAyah, int maxAyah){
		return getVerses(sura, minAyah, maxAyah, VERSE_TABLE);
	}
	
	public Cursor getVerses(int sura, int minAyah, int maxAyah, String table){
		if (!validDatabase()) return null;
		return database.query(table,
				new String[]{ COL_SURA, COL_AYAH, COL_TEXT },
				COL_SURA + "=" + sura + " and " + COL_AYAH + ">=" + minAyah +
				" and " + COL_AYAH + "<=" + maxAyah, null, null, null, null);
	}
	
	public Cursor getVerse(int sura, int ayah){
		return getVerses(sura, ayah, ayah);
	}
	
	public Cursor search(String query){
		return search(query, VERSE_TABLE);
	}
	
	public Cursor search(String query, String table){
		if (!validDatabase()) return null;
		
		return database.rawQuery("select " + COL_SURA + ", " + COL_AYAH +
				", " + COL_TEXT + " from " + table + " where " + COL_TEXT +
				" like '%" + query + "%' limit 50", null);
	}
	
	public void closeDatabase() {
		if (database != null)
			database.close();
	}
}
