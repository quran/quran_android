package com.quran.labs.androidquran.database;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranFileUtils;

import android.content.Context;
import android.database.Cursor;
import android.database.DefaultDatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;


public class DatabaseHandler {
  public static String COL_SURA = "sura";
  public static String COL_AYAH = "ayah";
  public static String COL_TEXT = "text";
  public static String VERSE_TABLE = "verses";
  public static String ARABIC_TEXT_TABLE = "arabic_text";

  public static String PROPERTIES_TABLE = "properties";
  public static String COL_PROPERTY = "property";
  public static String COL_VALUE = "value";

  private static final String MATCH_END = "</font>";
  private static final String ELLIPSES = "<b>...</b>";

  private static Map<String, DatabaseHandler> sDatabaseMap = new HashMap<>();

  private int mSchemaVersion = 1;
  private String mMatchString;
  private SQLiteDatabase mDatabase = null;

  public static synchronized DatabaseHandler getDatabaseHandler(
      Context context, String databaseName) {
    DatabaseHandler handler = sDatabaseMap.get(databaseName);
    if (handler == null) {
      handler = new DatabaseHandler(context.getApplicationContext(), databaseName);
      sDatabaseMap.put(databaseName, handler);
    }
    return handler;
  }

  private DatabaseHandler(Context context, String databaseName)
      throws SQLException {
    String base = QuranFileUtils.getQuranDatabaseDirectory(context);
    if (base == null) return;
    String path = base + File.separator + databaseName;
    Crashlytics.log("opening database file: " + path);
    try {
      mDatabase = SQLiteDatabase.openDatabase(path, null,
        SQLiteDatabase.NO_LOCALIZED_COLLATORS, new DefaultDatabaseErrorHandler());
    } catch (SQLiteDatabaseCorruptException sce) {
      Crashlytics.log("corrupt database: " + databaseName);
    } catch (SQLException se){
      Crashlytics.log("database file " + path +
          (new File(path).exists()? " exists" : " doesn't exist"));
      throw se;
    }

    mSchemaVersion = getSchemaVersion();
    mMatchString = "<font color=\"" +
        context.getResources().getColor(R.color.translation_highlight) +
        "\">";
  }

  public boolean validDatabase() {
    return mDatabase != null && mDatabase.isOpen();
  }

  public Cursor getVerses(int sura, int minAyah, int maxAyah) {
    return getVerses(sura, minAyah, maxAyah, VERSE_TABLE);
  }

  private int getProperty(@NonNull String column) {
    int value = 1;
    if (!validDatabase()) {
      return value;
    }

    Cursor cursor = null;
    try {
      cursor = mDatabase.query(PROPERTIES_TABLE, new String[]{ COL_VALUE },
          COL_PROPERTY + "= ?", new String[]{ column }, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        value = cursor.getInt(0);
      }
      return value;
    } catch (SQLException se) {
      return value;
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
  }

  public int getSchemaVersion() {
    return getProperty("schema_version");
  }

  public int getTextVersion() {
    return getProperty("text_version");
  }

  public Cursor getVerses(int sura, int minAyah, int maxAyah, String table) {
    return getVerses(sura, minAyah, sura, maxAyah, table);
  }

  public Cursor getVerses(int minSura, int minAyah, int maxSura,
                          int maxAyah, String table) {
    if (!validDatabase()) {
        return null;
    }

    StringBuilder whereQuery = new StringBuilder();
    whereQuery.append("(");

    if (minSura == maxSura) {
      whereQuery.append(COL_SURA)
          .append("=").append(minSura)
          .append(" and ").append(COL_AYAH)
          .append(">=").append(minAyah)
          .append(" and ").append(COL_AYAH)
          .append("<=").append(maxAyah);
    } else {
      // (sura = minSura and ayah >= minAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
          .append(minSura).append(" and ")
          .append(COL_AYAH).append(">=").append(minAyah).append(")");

      whereQuery.append(" or ");

      // (sura = maxSura and ayah <= maxAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
          .append(maxSura).append(" and ")
          .append(COL_AYAH).append("<=").append(maxAyah).append(")");

      whereQuery.append(" or ");

      // (sura > minSura and sura < maxSura)
      whereQuery.append("(").append(COL_SURA).append(">")
          .append(minSura).append(" and ")
          .append(COL_SURA).append("<")
          .append(maxSura).append(")");
    }

    whereQuery.append(")");

    return mDatabase.query(table,
        new String[] { "rowid as _id", COL_SURA, COL_AYAH, COL_TEXT },
        whereQuery.toString(), null, null, null,
        COL_SURA + "," + COL_AYAH);
  }

  public Cursor getVerse(int sura, int ayah) {
    return getVerses(sura, ayah, ayah);
  }

  public Cursor getVersesByIds(List<Integer> ids) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, idsSize = ids.size(); i < idsSize; i++) {
      if (i > 0) {
        builder.append(",");
      }
      builder.append(ids.get(i));
    }

    Timber.d("querying verses by ids for tags...");
    final String sql = "SELECT rowid as _id, " + COL_SURA + ", " + COL_AYAH + ", " + COL_TEXT +
        " FROM " + ARABIC_TEXT_TABLE + " WHERE rowid in(" + builder.toString() + ")";
    return mDatabase.rawQuery(sql, null);
  }

  public Cursor search(String query, boolean withSnippets) {
    return search(query, VERSE_TABLE, withSnippets);
  }

  public Cursor search(String q, String table, boolean withSnippets) {
    if (!validDatabase()) {
        return null;
    }

    String query = q;
    String operator = " like ";
    String whatTextToSelect = COL_TEXT;

    boolean useFullTextIndex = (mSchemaVersion > 1);
    if (useFullTextIndex) {
      operator = " MATCH ";
      query = query + "*";
    } else {
      query = "%" + query + "%";
    }

    int pos = 0;
    int found = 0;
    boolean done = false;
    while (!done) {
      int quote = query.indexOf("\"", pos);
      if (quote > -1) {
        found++;
        pos = quote + 1;
      } else {
        done = true;
      }
    }

    if (found % 2 != 0) {
      query = query.replaceAll("\"", "");
    }

    if (useFullTextIndex && withSnippets) {
      whatTextToSelect = "snippet(" + table + ", '" +
          mMatchString + "', '" + MATCH_END +
          "', '" + ELLIPSES + "', -1, 64)";
    }

    String qtext = "select rowid as " + BaseColumns._ID + ", " + COL_SURA + ", " + COL_AYAH +
        ", " + whatTextToSelect + " from " + table + " where " + COL_TEXT +
        operator + " ? " + " limit 150";
    Crashlytics.log("search query: " + qtext + ", query: " + query);

    try {
      return mDatabase.rawQuery(qtext, new String[]{ query });
    } catch (Exception e){
      Crashlytics.logException(e);
      return null;
    }
  }
}
