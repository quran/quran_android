package com.quran.labs.androidquran.database;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranFileUtils;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;


public class DatabaseHandler {

  private SQLiteDatabase mDatabase = null;
  private String mDatabasePath = null;

  public static String COL_SURA = "sura";
  public static String COL_AYAH = "ayah";
  public static String COL_TEXT = "text";
  public static String VERSE_TABLE = "verses";
  public static String ARABIC_TEXT_TABLE = "arabic_text";

  public static String PROPERTIES_TABLE = "properties";
  public static String COL_PROPERTY = "property";
  public static String COL_VALUE = "value";

  private int mSchemaVersion = 1;
  private String mMatchString;

  private static final String MATCH_END = "</font>";
  private static final String ELLIPSES = "<b>...</b>";

  public DatabaseHandler(Context context, String databaseName)
      throws SQLException {
    String base = QuranFileUtils.getQuranDatabaseDirectory(context);
    if (base == null) return;
    String path = base + File.separator + databaseName;
    Crashlytics.log("opening database file: " + path);
    try {
      mDatabase = SQLiteDatabase.openDatabase(path, null,
        SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    } catch (SQLException se){
      Crashlytics.log("database file " + path +
          (new File(path).exists()? " exists" : " doesn't exist"));
      throw se;
    }

    mSchemaVersion = getSchemaVersion();
    mDatabasePath = path;
    mMatchString = "<font color=\"" +
        context.getResources().getColor(R.color.translation_highlight) +
        "\">";
  }

  public boolean validDatabase() {
    return (mDatabase != null) && mDatabase.isOpen();
  }

  public boolean reopenDatabase() {
    try {
      mDatabase = SQLiteDatabase.openDatabase(mDatabasePath,
          null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public Cursor getVerses(int sura, int minAyah, int maxAyah) {
    return getVerses(sura, minAyah, maxAyah, VERSE_TABLE);
  }

  public int getSchemaVersion() {
    int version = 1;
    if (!validDatabase()) {
      return version;
    }

    Cursor result = null;
    try {
      result = mDatabase.query(PROPERTIES_TABLE, new String[]{COL_VALUE},
          COL_PROPERTY + "= ?", new String[]{"schema_version"},
          null, null, null);
      if ((result != null) && (result.moveToFirst()))
        version = result.getInt(0);
      if (result != null)
        result.close();
      return version;
    } catch (SQLException se) {
      if (result != null)
        result.close();
      return version;
    }
  }

  public int getTextVersion() {
    int version = 1;
    if (!validDatabase()) {
      return version;
    }

    Cursor result = null;
    try {
      result = mDatabase.query(PROPERTIES_TABLE, new String[]{COL_VALUE},
          COL_PROPERTY + "= ?", new String[]{"text_version"},
          null, null, null);
      if ((result != null) && (result.moveToFirst()))
        version = result.getInt(0);
      if (result != null)
        result.close();
      return version;
    } catch (SQLException se) {
      if (result != null)
        result.close();
      return version;
    }
  }

  public Cursor getVerses(int sura, int minAyah, int maxAyah, String table) {
    return getVerses(sura, minAyah, sura, maxAyah, table);
  }

  public Cursor getVerses(int minSura, int minAyah, int maxSura,
                          int maxAyah, String table) {
    if (!validDatabase()) {
      if (!reopenDatabase()) {
        return null;
      }
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
        new String[]{COL_SURA, COL_AYAH, COL_TEXT},
        whereQuery.toString(), null, null, null,
        COL_SURA + "," + COL_AYAH);
  }

  public Cursor getVerse(int sura, int ayah) {
    return getVerses(sura, ayah, ayah);
  }

  public Cursor search(String query, boolean withSnippets) {
    return search(query, VERSE_TABLE, withSnippets);
  }

  public Cursor search(String q, String table, boolean withSnippets) {
    if (!validDatabase()) {
      if (!reopenDatabase()) {
        return null;
      }
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

    if (useFullTextIndex && withSnippets) {
      whatTextToSelect = "snippet(" + table + ", '" +
          mMatchString + "', '" + MATCH_END +
          "', '" + ELLIPSES + "', -1, 64)";
    }

    String qtext = "select " + COL_SURA + ", " + COL_AYAH +
        ", " + whatTextToSelect + " from " + table + " where " + COL_TEXT +
        operator + " ? " + " limit 150";
    Crashlytics.log("search query: " + qtext + ", query: " + query);

    try {
      /* this check for getCount below is intentional.
       * due to the fact that many devices ship with an older sqlite
       * that doesn't support the snippet call. however, this doesn't
       * fail until you actually try to read any data (ie rawQuery
       * succeeds, but trying to do anything with the cursor throws
       * an exception).
       *
       * since we intentionally try and fall back to not using snippet,
       * we want this exception to be thrown here so we can handle it
       * gracefully if we can. consequently, we add the (awkward) check
       * for c.getCount, which, on 2.x, will cause a crash when our
       * query contains a snippet() call, thus allowing us to fallback
       * without killing the app.
       */
      final Cursor c = mDatabase.rawQuery(qtext, new String[]{query});
      if (c != null && c.getCount() >= 0) {
        return c;
      } else {
        return null;
      }
    } catch (Exception e){
      if (withSnippets && useFullTextIndex){
        Crashlytics.log("error querying, trying again without snippets...");
        return search(q, table, false);
      } else {
        Crashlytics.logException(e);
        return null;
      }
    }
  }

  public void closeDatabase() {
    DatabaseUtils.closeDatabase(mDatabase);
    mDatabase = null;
  }
}
