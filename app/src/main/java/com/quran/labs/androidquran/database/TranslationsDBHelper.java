package com.quran.labs.androidquran.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TranslationsDBHelper extends SQLiteOpenHelper {

   private static final String TAG = "TranslationsDBHelper";

   private static final String DB_NAME = "translations.db";
   private static final int DB_VERSION = 1;

   public static class TranslationsTable {
      public static final String TABLE_NAME = "translations";
      public static final String ID = "id";
      public static final String NAME = "name";
      public static final String TRANSLATOR = "translator";
      public static final String FILENAME = "filename";
      public static final String URL = "url";
      public static final String VERSION = "version";
   }

   private static final String CREATE_TRANSLATIONS_TABLE =
           "CREATE TABLE " + TranslationsTable.TABLE_NAME + "(" +
                   TranslationsTable.ID + " integer primary key, " +
                   TranslationsTable.NAME + " varchar not null, " +
                   TranslationsTable.TRANSLATOR + " varchar, " +
                   TranslationsTable.FILENAME + " varchar not null, " +
                   TranslationsTable.URL + " varchar, " +
                   TranslationsTable.VERSION + " integer not null default 0);";

   public TranslationsDBHelper(Context context) {
      super(context, DB_NAME, null, DB_VERSION);
   }

   @Override
   public void onCreate(SQLiteDatabase db) {
      db.execSQL(CREATE_TRANSLATIONS_TABLE);
   }

   @Override
   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
   }
}
