package com.quran.labs.androidquran.data;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;

public class SuraTimingDatabaseHandler {
   private SQLiteDatabase mDatabase = null;
   public static class TimingsTable {
      public static final String TABLE_NAME = "timings";
      public static final String COL_SURA = "sura";
      public static final String COL_AYAH = "ayah";
      public static final String COL_TIME = "time";
   }

   public SuraTimingDatabaseHandler(String path) throws SQLException {
      Log.d("stdbh", "opening: " + path);
      mDatabase = SQLiteDatabase.openDatabase(path, null,
              SQLiteDatabase.NO_LOCALIZED_COLLATORS);
   }

   public boolean validDatabase(){
      return (mDatabase == null)? false : mDatabase.isOpen();
   }

   public Cursor getAyahTimings(int sura){
      if (!validDatabase()) return null;
      return mDatabase.query(TimingsTable.TABLE_NAME,
              new String[]{ TimingsTable.COL_SURA,
                      TimingsTable.COL_AYAH, TimingsTable.COL_TIME },
              TimingsTable.COL_SURA + "=" + sura,
              null, null, null, TimingsTable.COL_AYAH + " ASC");
   }

   public void closeDatabase() {
      if (mDatabase != null){
         mDatabase.close();
         mDatabase = null;
      }
   }
}
