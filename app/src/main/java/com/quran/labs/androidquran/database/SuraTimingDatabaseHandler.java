package com.quran.labs.androidquran.database;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.crashlytics.android.Crashlytics;

import java.io.File;

public class SuraTimingDatabaseHandler {
  private SQLiteDatabase mDatabase = null;

  public static class TimingsTable {
    public static final String TABLE_NAME = "timings";
    public static final String COL_SURA = "sura";
    public static final String COL_AYAH = "ayah";
    public static final String COL_TIME = "time";
  }

  private static SuraTimingDatabaseHandler sDatabase;
  private static String sDatabasePath;
  private static final Object sDatabaseLock = new Object();

  public static SuraTimingDatabaseHandler getDatabaseHandler(String path) {
    synchronized (sDatabaseLock) {
      if (path.equals(sDatabasePath)) {
        return sDatabase;
      }

      if (sDatabase != null) {
        /* dangerous in theory, but since we cancel the async task on
         * stop, we should really not get in the case where we are closing
         * the old database from underneath the old thread */
        sDatabase.closeDatabase();
        sDatabasePath = null;
        sDatabase = null;
      }

      sDatabase = new SuraTimingDatabaseHandler(path);
      sDatabasePath = path;
      return sDatabase;
    }
  }

  private SuraTimingDatabaseHandler(String path) throws SQLException {
    Crashlytics.log("opening gapless data file, " + path);
    try {
      mDatabase = SQLiteDatabase.openDatabase(path, null,
          SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    } catch (SQLException se) {
      Crashlytics.log("database at " + path +
          (new File(path).exists() ? " exists" : " doesn't exist"));
      throw se;
    }
  }

  public boolean validDatabase() {
    return (mDatabase != null) && mDatabase.isOpen();
  }

  public Cursor getAyahTimings(int sura) {
    if (!validDatabase()) return null;
    try {
      return mDatabase.query(TimingsTable.TABLE_NAME,
          new String[]{TimingsTable.COL_SURA,
              TimingsTable.COL_AYAH, TimingsTable.COL_TIME},
          TimingsTable.COL_SURA + "=" + sura,
          null, null, null, TimingsTable.COL_AYAH + " ASC");
    } catch (Exception e) {
      return null;
    }
  }

  private void closeDatabase() {
    if (mDatabase != null) {
      try {
        mDatabase.close();
        mDatabase = null;
      } catch (Exception e) {
        // no op
      }
    }
  }
}
