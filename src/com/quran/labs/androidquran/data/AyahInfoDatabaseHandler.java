package com.quran.labs.androidquran.data;

import java.io.File;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.quran.labs.androidquran.util.QuranFileUtils;

public class AyahInfoDatabaseHandler {

	private SQLiteDatabase database = null;
	public static String COL_PAGE = "page_number";
	public static String COL_LINE = "line_number";
	public static String COL_SURA = "sura_number";
	public static String COL_AYAH = "ayah_number";
	public static String COL_POSITION = "position";
	public static String MIN_X = "min_x";
	public static String MIN_Y = "min_y";
	public static String MAX_X = "max_x";
	public static String MAX_Y = "max_y";
	public static String GLYPHS_TABLE = "glyphs";
	
	public AyahInfoDatabaseHandler(String databaseName) throws SQLException {
		String base = QuranFileUtils.getQuranDatabaseDirectory();
		if (base == null) return;
		String path = base + File.separator + databaseName;
		database = SQLiteDatabase.openDatabase(path, null,
				SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}
	
	public boolean validDatabase(){
		return (database == null)? false : database.isOpen();
	}
	
	public Cursor getVerseBounds(int sura, int ayah){
		if (!validDatabase()) return null;
		return database.query(GLYPHS_TABLE,
				new String[]{ COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION,
							   MIN_X, MIN_Y, MAX_X, MAX_Y },
				COL_SURA + "=" + sura + " and " + COL_AYAH + "=" + ayah,
				null, null, null, COL_POSITION);
	}
	
	public void closeDatabase() {
		if (database != null)
			database.close();
	}
}
