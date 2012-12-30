package com.quran.labs.androidquran.data;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;
import java.util.HashMap;

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
	
	public Rect getPageBounds(int page) {
		if (!validDatabase()) return null;
		String[] colNames = new String[] {
				"MIN("+MIN_X+")", "MIN("+MIN_Y+")",
				"MAX("+MAX_X+")", "MAX("+MAX_Y+")"};
		Cursor c = database.query(GLYPHS_TABLE, colNames, COL_PAGE + "=" + page,
				null, null, null, null);
		if (!c.moveToFirst()) return null;
		Rect r = new Rect(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3));
		c.close();
		return r;
	}
	
	// TODO Improve efficiency -AF
	public QuranAyah getVerseAtPoint(int page, float x, float y){
		if (!validDatabase())
			return null;
		Cursor cursor = database.query(GLYPHS_TABLE,
				new String[]{ COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION,
								MIN_X, MIN_Y, MAX_X, MAX_Y },
				COL_PAGE + "=" + page,
				null, null, null, COL_LINE);
		if (!cursor.moveToFirst())
			return null;
		HashMap<Integer, Integer[]> lines = new HashMap<Integer, Integer[]>();
		do {
			int lineNumber = cursor.getInt(1);
			int minY = cursor.getInt(6);
			int maxY = cursor.getInt(8);
			Integer[] lineBounds = lines.get(lineNumber);
			if (lineBounds == null) {
				Integer[] bounds = {minY, maxY};
				lines.put(lineNumber, bounds);
			} else {
				if (minY < lineBounds[0])
					lineBounds[0] = minY;
				if (maxY > lineBounds[1])
					lineBounds[1] = maxY;
			}
		} while (cursor.moveToNext());
		cursor.close();

      Float delta = null;
      Integer closestNeighbor = null;
		for (Integer line : lines.keySet()) {
			Integer[] bounds = lines.get(line);
			if (y >= bounds[0] && y <= bounds[1]){
				return getVerseAtPoint(page, line, x);
         }
         else if (y >= bounds[1]){
            // past this line
            if (delta == null){
               delta = y - bounds[1];
               closestNeighbor = line;
            }
            else if ((y - bounds[1]) < delta){
               delta = y - bounds[1];
               closestNeighbor = line;
            }
         }
         else if (bounds[0] >= y){
            // before this line
            if (delta == null){
               delta = bounds[0] - y;
               closestNeighbor = line;
            }
            else if ((bounds[0] - y) < delta){
               delta = bounds[0] - y;
               closestNeighbor = line;
            }
         }
		}

      if (delta != null && closestNeighbor != null){
         return getVerseAtPoint(page, closestNeighbor, x);
      }
		return null;
	}
	
	public QuranAyah getVerseAtPoint(int page, int line, float x){
		if (!validDatabase() || line < 1 || line > 15)
			return null;
		Cursor cursor = database.query(GLYPHS_TABLE,
				new String[]{ COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION,
								MIN_X, MIN_Y, MAX_X, MAX_Y },
				COL_PAGE + "=" + page + " and " + COL_LINE + "=" + line,
				null, null, null, COL_AYAH);
		if (!cursor.moveToFirst())
			return null;
		int suraNumber = cursor.getInt(2);
		HashMap<Integer, Integer[]> ayahs = new HashMap<Integer, Integer[]>();
		do {
			int ayahNumber = cursor.getInt(3);
			int minX = cursor.getInt(5);
			int maxX = cursor.getInt(7);
			Integer[] ayahBounds = ayahs.get(ayahNumber);
			if (ayahBounds == null) {
				Integer[] bounds = {minX, maxX};
				ayahs.put(ayahNumber, bounds);
			} else {
				if (minX < ayahBounds[0])
					ayahBounds[0] = minX;
				if (maxX > ayahBounds[1])
					ayahBounds[1] = maxX;
			}
		} while (cursor.moveToNext());
		cursor.close();
		for (Integer ayah : ayahs.keySet()) {
			Integer[] bounds = ayahs.get(ayah);
			if (x >= bounds[0] && x <= bounds[1])
				return new QuranAyah(suraNumber, ayah);
		}
		return null;
	}
	
	public void closeDatabase() {
		if (database != null)
			database.close();
	}
}
