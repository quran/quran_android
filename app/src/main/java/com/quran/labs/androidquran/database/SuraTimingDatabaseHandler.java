package com.quran.labs.androidquran.database;

import android.database.Cursor;
import android.database.DefaultDatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SuraTimingDatabaseHandler {
  private SQLiteDatabase database;

  public static class TimingsTable {
    static final String TABLE_NAME = "timings";
    static final String COL_SURA = "sura";
    static final String COL_AYAH = "ayah";
    static final String COL_TIME = "time";
  }

  private static Map<String, SuraTimingDatabaseHandler> sSuraDatabaseMap = new HashMap<>();

  public synchronized static SuraTimingDatabaseHandler getDatabaseHandler(String path) {
    SuraTimingDatabaseHandler handler = sSuraDatabaseMap.get(path);
    if (handler == null) {
      handler = new SuraTimingDatabaseHandler(path);
      sSuraDatabaseMap.put(path, handler);
    }
    return handler;
  }

  private SuraTimingDatabaseHandler(String path) throws SQLException {
    Crashlytics.log("opening gapless data file, " + path);
    try {
      database = SQLiteDatabase.openDatabase(path, null,
          SQLiteDatabase.NO_LOCALIZED_COLLATORS, new DefaultDatabaseErrorHandler());
    } catch (SQLiteDatabaseCorruptException sce) {
      Crashlytics.log("database corrupted: " + path);
      database = null;
    } catch (SQLException se) {
      Crashlytics.log("database at " + path +
          (new File(path).exists() ? " exists" : " doesn't exist"));
      Crashlytics.logException(se);
      database = null;
    }
  }

  private boolean validDatabase() {
    return database != null && database.isOpen();
  }

  public Cursor getAyahTimings(int sura) {
    if (!validDatabase()) { return null; }
    try {
      return database.query(TimingsTable.TABLE_NAME,
          new String[]{ TimingsTable.COL_SURA,
              TimingsTable.COL_AYAH, TimingsTable.COL_TIME },
          TimingsTable.COL_SURA + "=" + sura,
          null, null, null, TimingsTable.COL_AYAH + " ASC");
    } catch (Exception e) {
      return null;
    }
  }
}
