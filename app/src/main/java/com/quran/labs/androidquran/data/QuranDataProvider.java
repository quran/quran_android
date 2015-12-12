package com.quran.labs.androidquran.data;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.TranslationUtils;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import timber.log.Timber;

public class QuranDataProvider extends ContentProvider {

  public static String AUTHORITY = BuildConfig.APPLICATION_ID + ".data.QuranDataProvider";
  public static final Uri SEARCH_URI = Uri.parse("content://" + AUTHORITY + "/quran/search");

  public static final String VERSES_MIME_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE +
          "/vnd.com.quran.labs.androidquran";
  public static final String AYAH_MIME_TYPE =
      ContentResolver.CURSOR_ITEM_BASE_TYPE +
          "/vnd.com.quran.labs.androidquran";
  public static final String QURAN_ARABIC_DATABASE = QuranFileConstants.ARABIC_DATABASE;

  // UriMatcher stuff
  private static final int SEARCH_VERSES = 0;
  private static final int GET_VERSE = 1;
  private static final int SEARCH_SUGGEST = 2;
  private static final UriMatcher sURIMatcher = buildUriMatcher();

  private QuranSettings mQuranSettings;

  private static UriMatcher buildUriMatcher() {
    UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    matcher.addURI(AUTHORITY, "quran/search", SEARCH_VERSES);
    matcher.addURI(AUTHORITY, "quran/search/*", SEARCH_VERSES);
    matcher.addURI(AUTHORITY, "quran/search/*/*", SEARCH_VERSES);
    matcher.addURI(AUTHORITY, "quran/verse/#/#", GET_VERSE);
    matcher.addURI(AUTHORITY, "quran/verse/*/#/#", GET_VERSE);
    matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY,
        SEARCH_SUGGEST);
    matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*",
        SEARCH_SUGGEST);
    return matcher;
  }

  @Override
  public boolean onCreate() {
    mQuranSettings = QuranSettings.getInstance(getContext());
    return true;
  }

  @Override
  public Cursor query(@NonNull Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    Crashlytics.log("uri: " + uri.toString());
    switch (sURIMatcher.match(uri)) {
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

        if (selectionArgs.length == 1) {
          return search(selectionArgs[0]);
        } else {
          return search(selectionArgs[0], selectionArgs[1], true);
        }
      }
      case GET_VERSE: {
        return getVerse(uri);
      }
      default: {
        throw new IllegalArgumentException("Unknown Uri: " + uri);
      }
    }
  }

  private Cursor search(String query) {
    if (QuranUtils.doesStringContainArabic(query) &&
        QuranFileUtils.hasTranslation(getContext(), QURAN_ARABIC_DATABASE)) {
      Cursor c = search(query, QURAN_ARABIC_DATABASE, true);
      if (c != null) {
        return c;
      }
    }

    String active = getActiveTranslation();
    if (TextUtils.isEmpty(active)) {
      return null;
    }
    return search(query, active, true);
  }

  private String getActiveTranslation() {
    String db = mQuranSettings.getActiveTranslation();
    if (!TextUtils.isEmpty(db)) {
      if (QuranFileUtils.hasTranslation(getContext(), db)) {
        return db;
      }
      // our active database no longer exists, remove the pref
      mQuranSettings.removeActiveTranslation();
    }

    try {
      Crashlytics.log("couldn't find database, searching for another..");
      TranslationsDBAdapter adapter =
          new TranslationsDBAdapter(getContext());
      List<TranslationItem> items = adapter.getTranslations();
      if (items != null && items.size() > 0) {
        return TranslationUtils
            .getDefaultTranslation(getContext(), items);
      }
    } catch (Exception e) {
      Crashlytics.logException(e);
    }
    return null;
  }

  private Cursor getSuggestions(String query) {
    if (query.length() < 3) {
      return null;
    }

    boolean haveArabic = false;
    if (QuranUtils.doesStringContainArabic(query) &&
        QuranFileUtils.hasTranslation(getContext(), QURAN_ARABIC_DATABASE)) {
      haveArabic = true;
    }

    boolean haveTranslation = false;
    String active = getActiveTranslation();
    if (!TextUtils.isEmpty(active)) {
      haveTranslation = true;
    }

    int numItems = (haveArabic ? 1 : 0) + (haveTranslation ? 1 : 0);
    if (numItems == 0) {
      return null;
    }

    String[] items = new String[numItems];
    if (haveArabic) {
      items[0] = QURAN_ARABIC_DATABASE;
    }

    if (haveTranslation) {
      items[numItems - 1] = active;
    }

    String[] cols = new String[] { BaseColumns._ID,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
        SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID };
    MatrixCursor mc = new MatrixCursor(cols);

    Context context = getContext();
    boolean gotResults = false;
    for (String item : items) {
      if (gotResults) {
        continue;
      }

      Cursor suggestions = null;
      try {
        suggestions = search(query, item, false);
        if (suggestions != null && suggestions.moveToFirst()) {
          do {
            int sura = suggestions.getInt(1);
            int ayah = suggestions.getInt(2);
            String text = suggestions.getString(3);
            String foundText = context
                .getString(R.string.found_in_sura) +
                " " + QuranInfo.getSuraName(context, sura, false) +
                ", " + context.getString(R.string.quran_ayah, ayah);

            gotResults = true;
            MatrixCursor.RowBuilder row = mc.newRow();
            int id = suggestions.getInt(0);

            row.add(id);
            row.add(text);
            row.add(foundText);
            row.add(id);
          } while (suggestions.moveToNext());
        }
      } finally {
        DatabaseUtils.closeCursor(suggestions);
      }
    }

    return mc;
  }

  private Cursor search(String query, String language, boolean wantSnippets) {
    Timber.d("q: " + query + ", l: " + language);
    if (language == null) {
      return null;
    }

    final DatabaseHandler handler = DatabaseHandler.getDatabaseHandler(getContext(), language);
    return handler.search(query, wantSnippets);
  }

  private Cursor getVerse(Uri uri) {
    int sura = 1;
    int ayah = 1;
    String langType = getActiveTranslation();
    String lang = (TextUtils.isEmpty(langType)) ? null : langType;
    if (lang == null) {
      return null;
    }

    List<String> parts = uri.getPathSegments();
    for (String s : parts) {
      Timber.d("uri part: " + s);
    }

    final DatabaseHandler handler = DatabaseHandler.getDatabaseHandler(getContext(), lang);
    return handler.getVerse(sura, ayah);
  }

  @Override
  public String getType(Uri uri) {
    switch (sURIMatcher.match(uri)) {
      case SEARCH_VERSES: {
        return VERSES_MIME_TYPE;
      }
      case GET_VERSE: {
        return AYAH_MIME_TYPE;
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
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection,
      String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }
}
