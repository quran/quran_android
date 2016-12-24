package com.quran.labs.androidquran.data;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.RectF;

import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;

public class AyahInfoDatabaseHandler {

  private static final String COL_PAGE = "page_number";
  private static final String COL_LINE = "line_number";
  private static final String COL_SURA = "sura_number";
  private static final String COL_AYAH = "ayah_number";
  private static final String COL_POSITION = "position";
  private static final String MIN_X = "min_x";
  private static final String MIN_Y = "min_y";
  private static final String MAX_X = "max_x";
  private static final String MAX_Y = "max_y";
  private static final String GLYPHS_TABLE = "glyphs";

  private final SQLiteDatabase database;

  AyahInfoDatabaseHandler(Context context, String databaseName) throws SQLException {
    String base = QuranFileUtils.getQuranAyahDatabaseDirectory(context);
    if (base == null) {
      database = null;
    } else {
      String path = base + File.separator + databaseName;
      database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }
  }

  boolean validDatabase() {
    return database != null && database.isOpen();
  }

  public Cursor getVersesBoundsForPage(int page) {
    if (!validDatabase()) return null;
    return database.query(GLYPHS_TABLE,
        new String[]{ COL_PAGE, COL_LINE, COL_SURA, COL_AYAH,
            COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y },
        COL_PAGE + "=" + page,
        null, null, null,
        COL_SURA + "," + COL_AYAH + "," + COL_POSITION);
  }

  public RectF getPageBounds(int page) {
    if (!validDatabase()) {
      return null;
    }

    Cursor c = null;
    try {
      String[] colNames = new String[]{
          "MIN(" + MIN_X + ")", "MIN(" + MIN_Y + ")",
          "MAX(" + MAX_X + ")", "MAX(" + MAX_Y + ")" };
      c = database.query(GLYPHS_TABLE,
          colNames, COL_PAGE + "=" + page, null, null, null, null);
      if (!c.moveToFirst()) {
        return null;
      }
      return new RectF(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3));
    } catch (Exception e) {
      return null;
    } finally {
      DatabaseUtils.closeCursor(c);
    }
  }
}
