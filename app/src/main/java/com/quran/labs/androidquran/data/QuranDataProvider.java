package com.quran.labs.androidquran.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class QuranDataProvider extends ContentProvider {

  public static String AUTHORITY = BuildConfig.APPLICATION_ID + ".data.QuranDataProvider";
  public static final Uri SEARCH_URI = Uri.parse("content://" + AUTHORITY + "/quran/search");

  public static final String VERSES_MIME_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.quran.labs.androidquran";
  public static final String QURAN_ARABIC_DATABASE = QuranFileConstants.ARABIC_DATABASE;

  // UriMatcher stuff
  private static final int SEARCH_VERSES = 0;
  private static final int SEARCH_SUGGEST = 1;
  private static final UriMatcher uriMatcher = buildUriMatcher();

  private boolean didInject;
  @Inject QuranDisplayData quranDisplayData;
  @Inject TranslationsDBAdapter translationsDBAdapter;
  @Inject QuranFileUtils quranFileUtils;

  private static UriMatcher buildUriMatcher() {
    UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    matcher.addURI(AUTHORITY, "quran/search", SEARCH_VERSES);
    matcher.addURI(AUTHORITY, "quran/search/*", SEARCH_VERSES);
    matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
    matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
    return matcher;
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(@NonNull Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    Context context = getContext();
    if (!didInject) {
      Context appContext = context == null ? null : context.getApplicationContext();
      if (appContext instanceof QuranApplication) {
        ((QuranApplication) appContext).getApplicationComponent().inject(this);
        didInject = true;
      } else {
        Timber.e("unable to inject QuranDataProvider");
        return null;
      }
    }

    Timber.d("uri: %s", uri.toString());
    switch (uriMatcher.match(uri)) {
      case SEARCH_SUGGEST: {
        if (selectionArgs == null) {
          throw new IllegalArgumentException(
              "selectionArgs must be provided for the Uri: " + uri);
        }

        return getSuggestions(selectionArgs[0]);
      }
      case SEARCH_VERSES: {
        if (selectionArgs == null) {
          throw new IllegalArgumentException(
              "selectionArgs must be provided for the Uri: " + uri);
        }

        return search(selectionArgs[0]);
      }
      default: {
        throw new IllegalArgumentException("Unknown Uri: " + uri);
      }
    }
  }

  private Cursor search(String query) {
    return search(query, getAvailableTranslations());
  }

  private List<LocalTranslation> getAvailableTranslations() {
    return translationsDBAdapter.getTranslations();
  }

  private Cursor getSuggestions(String query) {
    if (query.length() < 3) {
      return null;
    }

    final boolean queryIsArabic = QuranUtils.doesStringContainArabic(query);
    final boolean haveArabic = queryIsArabic &&
        quranFileUtils.hasTranslation(getContext(), QURAN_ARABIC_DATABASE);

    List<LocalTranslation> translations = getAvailableTranslations();
    if (translations.size() == 0 && (queryIsArabic && !haveArabic)) {
      return null;
    }

    int total = translations.size();
    int start = haveArabic ? -1 : 0;

    String[] cols = new String[] { BaseColumns._ID,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
        SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID };
    MatrixCursor mc = new MatrixCursor(cols);

    Context context = getContext();
    boolean gotResults = false;
    boolean likelyHaveMoreResults = false;
    for (int i = start; i < total; i++) {
      if (gotResults) {
        continue;
      }

      String database;
      if (i < 0) {
        database = QURAN_ARABIC_DATABASE;
        if (!quranFileUtils.hasArabicSearchDatabase()) {
          continue;
        }
      } else {
        LocalTranslation translation = translations.get(i);
        // skip non-arabic databases if the query is in arabic
        if (queryIsArabic || "ar".equals(translation.getLanguageCode())) {
          // skip arabic databases even when the query is in arabic since
          // searching Arabic tafaseer causes a lot of noise on the search
          // results and is confusing.
          continue;
        }
        database = translation.getFilename();
      }

      Cursor suggestions = null;
      try {
        suggestions = search(query, database, false);
        if (context != null && suggestions != null && suggestions.moveToFirst()) {
          if (suggestions.getCount() > 5) {
            likelyHaveMoreResults = true;
          }

          int results = 0;
          do {
            if (results == 5) {
              break;
            }
            int sura = suggestions.getInt(1);
            int ayah = suggestions.getInt(2);
            String text = suggestions.getString(3);
            String foundText = context.getString(
                R.string.found_in_sura, quranDisplayData.getSuraName(context, sura, false), ayah);

            gotResults = true;
            MatrixCursor.RowBuilder row = mc.newRow();
            int id = suggestions.getInt(0);

            row.add(id);
            row.add(text);
            row.add(foundText);
            row.add(id);
            results++;
          } while (suggestions.moveToNext());
        }
      } finally {
        DatabaseUtils.closeCursor(suggestions);
      }
    }

    if (context != null && (queryIsArabic || likelyHaveMoreResults)) {
      mc.addRow(new Object[] {
          -1, context.getString(R.string.search_full_results),
          context.getString(R.string.search_entire_mushaf), -1
      });
    }
    return mc;
  }

  private Cursor search(String query, List<LocalTranslation> translations) {
    Timber.d("query: %s", query);

    final Context context = getContext();
    final boolean queryIsArabic = QuranUtils.doesStringContainArabic(query);
    final boolean haveArabic = queryIsArabic &&
        quranFileUtils.hasTranslation(context, QURAN_ARABIC_DATABASE);
    if (translations.size() == 0 && (queryIsArabic && !haveArabic)) {
      return null;
    }

    int start = haveArabic ? -1 : 0;
    int total = translations.size();

    for (int i = start; i < total; i++) {
      String databaseName;
      if (i < 0) {
        databaseName = QURAN_ARABIC_DATABASE;
      } else {
        LocalTranslation translation = translations.get(i);
        // skip non-arabic databases if the query is in arabic
        if (queryIsArabic || "ar".equals(translation.getLanguageCode())) {
          // skip arabic databases always since it's confusing to people for now.
          // in the future, can think of better ways to enable tafseer search.
          continue;
        }
        databaseName = translation.getFilename();
      }

      Cursor cursor = search(query, databaseName, true);
      if (cursor != null && cursor.getCount() > 0) {
        return cursor;
      }
    }
    return null;
  }

  private Cursor search(String query, String databaseName, boolean wantSnippets) {
    final DatabaseHandler handler =
        DatabaseHandler.getDatabaseHandler(getContext(), databaseName, quranFileUtils);
    return handler.search(query, wantSnippets, QURAN_ARABIC_DATABASE.equals(databaseName));
  }

  @Override
  public String getType(@NonNull Uri uri) {
    switch (uriMatcher.match(uri)) {
      case SEARCH_VERSES: {
        return VERSES_MIME_TYPE;
      }
      case SEARCH_SUGGEST: {
        return SearchManager.SUGGEST_MIME_TYPE;
      }
      default: {
        throw new IllegalArgumentException("Unknown URL " + uri);
      }
    }
  }

  @Override
  public Uri insert(@NonNull Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(@NonNull Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }
}
