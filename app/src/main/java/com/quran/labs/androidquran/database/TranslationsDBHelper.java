package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class TranslationsDBHelper extends SQLiteOpenHelper {

  private static final String DB_NAME = "translations.db";
  private static final int DB_VERSION = 2;

  TranslationsDBHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(TranslationsTable.getCreateTableSql(TranslationsTable.TABLE_NAME));
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2) {
      // a new column is added and columns are re-arranged
      final String BACKUP_TABLE = TranslationsTable.TABLE_NAME + "_backup";
      db.execSQL(TranslationsTable.getCreateTableSql(BACKUP_TABLE));

      db.execSQL("INSERT INTO " + BACKUP_TABLE + " (" +
          TranslationsTable.ID + ", " +
          TranslationsTable.NAME + ", " +
          TranslationsTable.TRANSLATOR + ", " +
          TranslationsTable.FILENAME + ", " +
          TranslationsTable.URL + ", " +
          TranslationsTable.VERSION + ")" +
          "SELECT " + TranslationsTable.ID + ", " +
          TranslationsTable.NAME + ", " +
          TranslationsTable.TRANSLATOR + ", " +
          TranslationsTable.FILENAME + ", " +
          TranslationsTable.URL + ", " +
          TranslationsTable.VERSION +
          " FROM " + TranslationsTable.TABLE_NAME + ";");

      db.execSQL("DROP TABLE " + TranslationsTable.TABLE_NAME);
      db.execSQL("ALTER TABLE " + BACKUP_TABLE +
          " RENAME TO " + TranslationsTable.TABLE_NAME);
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

    static String getCreateTableSql(String tableName) {
      return "CREATE TABLE " + tableName + "(" +
          ID + " integer primary key, " +
          NAME + " varchar not null, " +
          TRANSLATOR + " varchar, " +
          TRANSLATOR_FOREIGN + " varchar, " +
          FILENAME + " varchar not null, " +
          URL + " varchar, " +
          VERSION + " integer not null default 0);";
    }
  }
}
