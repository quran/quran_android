package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DefaultDatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.provider.BaseColumns;
import android.util.SparseArray;

import com.quran.common.search.ArabicSearcher;
import com.quran.common.search.DefaultSearcher;
import com.quran.common.search.Searcher;
import com.quran.labs.androidquran.R;
import com.quran.data.model.QuranText;
import com.quran.labs.androidquran.data.QuranFileConstants;
import com.quran.data.model.VerseRange;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.TranslationUtil;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import timber.log.Timber;


public class DatabaseHandler {
  private static final String COL_SURA = "sura";
  private static final String COL_AYAH = "ayah";
  private static final String COL_TEXT = "text";
  private static final String VERSE_TABLE = "verses";
  public static final String ARABIC_TEXT_TABLE = "arabic_text";
  public static final String SHARE_TEXT_TABLE = "share_text";

  private static final String PROPERTIES_TABLE = "properties";
  private static final String COL_PROPERTY = "property";
  private static final String COL_VALUE = "value";

  private static final String MATCH_END = "</font>";
  private static final String ELLIPSES = "<b>...</b>";

  private static Map<String, DatabaseHandler> databaseMap = new HashMap<>();

  private int schemaVersion = 1;
  private SQLiteDatabase database = null;

  private final Searcher defaultSearcher;
  private final Searcher arabicSearcher;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef( { TextType.ARABIC, TextType.TRANSLATION } )
  public @interface TextType {
    int ARABIC = 0;
    int TRANSLATION = 1;
  }

  public static synchronized DatabaseHandler getDatabaseHandler(
      Context context, String databaseName, QuranFileUtils quranFileUtils) {
    DatabaseHandler handler = databaseMap.get(databaseName);
    if (handler == null) {
      handler = new DatabaseHandler(context.getApplicationContext(), databaseName, quranFileUtils);
      databaseMap.put(databaseName, handler);
    }
    return handler;
  }

  public static synchronized void clearDatabaseHandlerIfExists(String databaseName) {
    try {
      DatabaseHandler handler = databaseMap.remove(databaseName);
      if (handler != null) {
        handler.database.close();
        databaseMap.remove(databaseName);
      }
    } catch (Exception e) {
      Timber.e(e);
    }
  }

  private DatabaseHandler(Context context,
                          String databaseName,
                          QuranFileUtils quranFileUtils) throws SQLException {
    // initialize the searchers first
    final String matchString =
        "<font color=\"" +
            ContextCompat.getColor(context, R.color.translation_highlight) +
            "\">";
    defaultSearcher = new DefaultSearcher(matchString, MATCH_END, ELLIPSES);
    arabicSearcher = new ArabicSearcher(defaultSearcher, matchString, MATCH_END);

    // if there's no Quran base directory, there are no databases
    String base = quranFileUtils.getQuranDatabaseDirectory(context);
    if (base == null) return;

    String path = base + File.separator + databaseName;
    Timber.d("opening database file: %s", path);

    try {
      database = SQLiteDatabase.openDatabase(path, null,
        SQLiteDatabase.NO_LOCALIZED_COLLATORS, new DefaultDatabaseErrorHandler());
    } catch (SQLiteDatabaseCorruptException sce) {
      Timber.d("corrupt database: %s", databaseName);
      throw sce;
    } catch (SQLException se){
      Timber.d("database file " + path +
          (new File(path).exists()? " exists" : " doesn't exist"));
      throw se;
    }

    schemaVersion = getSchemaVersion();
  }

  public boolean validDatabase() {
    return database != null && database.isOpen();
  }

  private Cursor getVerses(int sura, int minAyah, int maxAyah) {
    return getVerses(sura, minAyah, maxAyah, VERSE_TABLE);
  }

  private int getProperty(@NonNull String column) {
    int value = 1;
    if (!validDatabase()) {
      return value;
    }

    Cursor cursor = null;
    try {
      cursor = database.query(PROPERTIES_TABLE, new String[]{ COL_VALUE },
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

  private Cursor getVerses(int sura, int minAyah, int maxAyah, String table) {
    return getVerses(sura, minAyah, sura, maxAyah, table);
  }

  /**
   * @deprecated use {@link #getVerses(VerseRange, int)} instead
   *
   * @param minSura start sura
   * @param minAyah start ayah
   * @param maxSura end sura
   * @param maxAyah end ayah
   * @param table the table
   * @return a Cursor with the data
   */
  public Cursor getVerses(int minSura, int minAyah, int maxSura,
                          int maxAyah, String table) {
    // pass -1 for verses since this is used internally only and the field isn't needed.
    return getVersesInternal(new VerseRange(minSura, minAyah, maxSura, maxAyah, -1), table);
  }

  public List<QuranText> getVerses(VerseRange verses, @TextType int textType) {
    Cursor cursor = null;
    List<QuranText> results = new ArrayList<>();
    final Set<Integer> toLookup = new HashSet<>();

    String table = textType == TextType.ARABIC ? ARABIC_TEXT_TABLE : VERSE_TABLE;
    try {
      cursor = getVersesInternal(verses, table);
      while (cursor != null && cursor.moveToNext()) {
        int sura = cursor.getInt(1);
        int ayah = cursor.getInt(2);
        String text = cursor.getString(3);

        final QuranText quranText = new QuranText(sura, ayah, text, null);
        results.add(quranText);

        final Integer hyperlinkId = TranslationUtil.getHyperlinkAyahId(quranText);
        if (hyperlinkId != null) {
          toLookup.add(hyperlinkId);
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    boolean didWrite = false;
    if (!toLookup.isEmpty()) {
      final StringBuilder toExpandBuilder = new StringBuilder();
      for (Integer id : toLookup) {
        if (didWrite) {
          toExpandBuilder.append(",");
        } else {
          didWrite = true;
        }
        toExpandBuilder.append(id);
      }
      return expandHyperlinks(table, results, toExpandBuilder.toString());
    }
    return results;
  }

  private List<QuranText> expandHyperlinks(String table, List<QuranText> data, String rowIds) {
    SparseArray<String> expansions = new SparseArray<>();

    Cursor cursor = null;
    try {
      cursor = database.query(table, new String[]{ "rowid as _id", COL_TEXT },
          "rowid in (" + rowIds + ")", null, null, null, "rowid");
      while (cursor != null && cursor.moveToNext()) {
        int id = cursor.getInt(0);
        String text = cursor.getString(1);
        expansions.put(id, text);
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    List<QuranText> result = new ArrayList<>();
    for (int i = 0, size = data.size(); i < size; i++) {
      final QuranText ayah = data.get(i);
      final Integer linkId = TranslationUtil.getHyperlinkAyahId(ayah);
      if (linkId == null) {
        result.add(ayah);
      } else {
        final String expandedText = expansions.get(linkId);
        result.add(new QuranText(ayah.getSura(), ayah.getAyah(), ayah.getText(), expandedText));
      }
    }
    return result;
  }

  private Cursor getVersesInternal(VerseRange verses, String table) {
    if (!validDatabase()) {
        return null;
    }

    StringBuilder whereQuery = new StringBuilder();
    whereQuery.append("(");

    if (verses.startSura == verses.endingSura) {
      whereQuery.append(COL_SURA)
          .append("=").append(verses.startSura)
          .append(" and ").append(COL_AYAH)
          .append(">=").append(verses.startAyah)
          .append(" and ").append(COL_AYAH)
          .append("<=").append(verses.endingAyah);
    } else {
      // (sura = minSura and ayah >= minAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
          .append(verses.startSura).append(" and ")
          .append(COL_AYAH).append(">=").append(verses.startAyah).append(")");

      whereQuery.append(" or ");

      // (sura = maxSura and ayah <= maxAyah)
      whereQuery.append("(").append(COL_SURA).append("=")
          .append(verses.endingSura).append(" and ")
          .append(COL_AYAH).append("<=").append(verses.endingAyah).append(")");

      whereQuery.append(" or ");

      // (sura > minSura and sura < maxSura)
      whereQuery.append("(").append(COL_SURA).append(">")
          .append(verses.startSura).append(" and ")
          .append(COL_SURA).append("<")
          .append(verses.endingSura).append(")");
    }

    whereQuery.append(")");

    return database.query(table,
        new String[] { "rowid as _id", COL_SURA, COL_AYAH, COL_TEXT },
        whereQuery.toString(), null, null, null,
        COL_SURA + "," + COL_AYAH);
  }

  /**
   * @deprecated use {@link #getVerses(VerseRange, int)} instead
   * @param sura the sura
   * @param ayah the ayah
   * @return the result
   */
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
        " FROM " + QuranFileConstants.ARABIC_SHARE_TABLE +
        " WHERE rowid in(" + builder.toString() + ")";
    return database.rawQuery(sql, null);
  }

  public Cursor search(String query, boolean withSnippets, boolean isArabicDatabase) {
    return search(query, VERSE_TABLE, withSnippets, isArabicDatabase);
  }

  public Cursor search(String q, String table, boolean withSnippets, boolean isArabicDatabase) {
    if (!validDatabase()) {
        return null;
    }

    String searchText = q;
    int pos = 0;
    int found = 0;
    boolean done = false;
    while (!done) {
      int quote = searchText.indexOf("\"", pos);
      if (quote > -1) {
        found++;
        pos = quote + 1;
      } else {
        done = true;
      }
    }

    if (found % 2 != 0) {
      searchText = searchText.replaceAll("\"", "");
    }

    final Searcher searcher;
    if (isArabicDatabase) {
      searcher = arabicSearcher;
    } else {
      searcher = defaultSearcher;
    }


    final boolean useFullTextIndex = (schemaVersion > 1 && !isArabicDatabase);
    String qtext = searcher.getQuery(withSnippets, useFullTextIndex, table,
        "rowid as " + BaseColumns._ID + ", " + COL_SURA + ", " + COL_AYAH, COL_TEXT) +
        " " + searcher.getLimit(withSnippets);
    searchText = searcher.processSearchText(searchText, useFullTextIndex);
    Timber.d("search query: " + qtext + ", query: " + searchText);

    final String[] columns = new String[] { BaseColumns._ID, COL_SURA, COL_AYAH, COL_TEXT };
    try {
      return searcher.runQuery(database, qtext, searchText, q, withSnippets, columns);
    } catch (Exception e) {
      Timber.e(e);
      return null;
    }
  }
}
