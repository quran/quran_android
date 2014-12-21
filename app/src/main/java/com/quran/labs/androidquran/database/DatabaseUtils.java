package com.quran.labs.androidquran.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DatabaseUtils {

  /* one day, if we ever target jellybean+, we can replace
   * these two methods with a single "closeSilently(Closeable c)"
   * method. however, up until jellybean, neither Cursor nor
   * SQLiteDatabase implement Closeable.
   */

  public static void closeDatabase(SQLiteDatabase database) {
    if (database != null) {
      try {
        database.close();
      } catch (Exception e) {
        // no op
      }
    }
  }

  public static void closeCursor(Cursor cursor) {
    if (cursor != null) {
      try {
        cursor.close();
      } catch (Exception e) {
        // no op
      }
    }
  }
}
