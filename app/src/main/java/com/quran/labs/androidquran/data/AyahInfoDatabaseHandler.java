package com.quran.labs.androidquran.data;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.RectF;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  private static Map<String, AyahInfoDatabaseHandler> ayahInfoCache = new HashMap<>();

  private final SQLiteDatabase database;

  static AyahInfoDatabaseHandler getAyahInfoDatabaseHandler(Context context, String databaseName) {
    AyahInfoDatabaseHandler handler = ayahInfoCache.get(databaseName);
    if (handler == null) {
      try {
        AyahInfoDatabaseHandler db = new AyahInfoDatabaseHandler(context, databaseName);
        if (db.validDatabase()) {
          ayahInfoCache.put(databaseName, db);
          handler = db;
        }
      } catch (SQLException sqlException) {
        // it's okay, we'll try again later
      }
    }
    return handler;
  }

  private AyahInfoDatabaseHandler(Context context, String databaseName) throws SQLException {
    String base = QuranFileUtils.getQuranAyahDatabaseDirectory(context);
    if (base == null) {
      database = null;
    } else {
      String path = base + File.separator + databaseName;
      database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }
  }

  private boolean validDatabase() {
    return database != null && database.isOpen();
  }

  @NonNull
  public RectF getPageBounds(int page) {
    Cursor c = null;
    try {
      String[] colNames = new String[]{
          "MIN(" + MIN_X + ")", "MIN(" + MIN_Y + ")",
          "MAX(" + MAX_X + ")", "MAX(" + MAX_Y + ")" };
      c = database.query(GLYPHS_TABLE, colNames, COL_PAGE + "=" + page, null, null, null, null);
      if (c.moveToFirst()) {
        return new RectF(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3));
      } else {
        throw new IllegalArgumentException("getPageBounds() on a non-existent page: " + page);
      }
    } finally {
      DatabaseUtils.closeCursor(c);
    }
  }

  @NonNull
  public Map<String, List<AyahBounds>> getVersesBoundsForPage(int page) {
    Map<String, List<AyahBounds>> result = new HashMap<>();
    Cursor cursor = null;
    try {
      cursor = getVersesBoundsCursorForPage(page);
      while (cursor.moveToNext()) {
        int sura = cursor.getInt(2);
        int ayah = cursor.getInt(3);
        String key = sura + ":" + ayah;
        List<AyahBounds> bounds = result.get(key);
        if (bounds == null) {
          bounds = new ArrayList<>();
        }

        AyahBounds last = null;
        if (bounds.size() > 0) {
          last = bounds.get(bounds.size() - 1);
        }

        AyahBounds bound = new AyahBounds(cursor.getInt(1),
            cursor.getInt(4), cursor.getInt(5),
            cursor.getInt(6), cursor.getInt(7),
            cursor.getInt(8));
        if (last != null && last.getLine() == bound.getLine()) {
          last.engulf(bound);
        } else {
          bounds.add(bound);
        }
        result.put(key, bounds);
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    return result;
  }

  private Cursor getVersesBoundsCursorForPage(int page) {
    return database.query(GLYPHS_TABLE,
        new String[]{ COL_PAGE, COL_LINE, COL_SURA, COL_AYAH,
            COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y },
        COL_PAGE + "=" + page,
        null, null, null,
        COL_SURA + "," + COL_AYAH + "," + COL_POSITION);
  }
}
