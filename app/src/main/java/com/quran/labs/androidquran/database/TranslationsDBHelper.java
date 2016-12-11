package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import timber.log.Timber;

class TranslationsDBHelper extends SQLiteOpenHelper {

  private static final String DB_NAME = "translations.db";
  private static final int DB_VERSION = 2;
  private static final String CREATE_TRANSLATIONS_TABLE =
      "CREATE TABLE " + TranslationsTable.TABLE_NAME + "(" +
          TranslationsTable.ID + " integer primary key, " +
          TranslationsTable.NAME + " varchar not null, " +
          TranslationsTable.TRANSLATOR + " varchar, " +
          TranslationsTable.TRANSLATOR_FOREIGN + " varchar, " +
          TranslationsTable.FILENAME + " varchar not null, " +
          TranslationsTable.URL + " varchar, " +
          TranslationsTable.VERSION + " integer not null default 0);";

  TranslationsDBHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(CREATE_TRANSLATIONS_TABLE);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (newVersion <= oldVersion) {
      Timber.w("Can't downgrade from version %d to version %d", oldVersion, newVersion);
      return;
    }

    if (oldVersion < 2) {
      // a new column is added and columns are re-arranged
      db.execSQL("DROP TABLE IF EXISTS " + TranslationsTable.TABLE_NAME);
      db.execSQL(CREATE_TRANSLATIONS_TABLE);
    }
  }

  static class TranslationsTable {
    static final String TABLE_NAME = "translations";
    static final String ID = "id";
    static final String NAME = "name";
    static final String TRANSLATOR = "translator";
    static final String TRANSLATOR_FOREIGN = "translator_foreign";
    static final String FILENAME = "filename";
    static final String URL = "url";
    static final String VERSION = "version";
  }
}
