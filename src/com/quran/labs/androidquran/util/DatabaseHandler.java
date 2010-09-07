package com.quran.labs.androidquran.util;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;


public class DatabaseHandler {
	
	private SQLiteDatabase database = null;
	private static String TABLE = "verses";
	private static String COL_SURA = "sura";
	private static String COL_AYAH = "ayah";
	private static String COL_TEXT = "text";
	
	public DatabaseHandler(String databaseName) throws SQLException {
		String base = QuranUtils.getQuranDatabaseDirectory();
		if (base == null) return;
		String path = base + "/quran." + databaseName + ".db";
		database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}
	
	public boolean validDatabase(){
		return (database == null)? false : database.isOpen();
	}
	
	public Cursor getVerses(int sura, int minAyah, int maxAyah){
		if (!validDatabase()) return null;
		return database.query(TABLE, new String[]{ COL_SURA, COL_AYAH, COL_TEXT },
				COL_SURA + "=" + sura + " and " + COL_AYAH + ">=" + minAyah +
				" and " + COL_AYAH + "<=" + maxAyah, null, null, null, null);
	}
	
	public void closeDatabase(){ if (database != null) database.close(); }
}
