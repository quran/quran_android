package com.quran.labs.androidquran.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsDBHelper @Inject constructor(context: Context) :
  SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  companion object {
    private const val DB_NAME = "translations.db"
    private const val DB_VERSION = 5
    private const val CREATE_TRANSLATIONS_TABLE =
      "CREATE TABLE " + TranslationsTable.TABLE_NAME + "(" +
          TranslationsTable.ID + " integer primary key, " +
          TranslationsTable.NAME + " varchar not null, " +
          TranslationsTable.TRANSLATOR + " varchar, " +
          TranslationsTable.TRANSLATOR_FOREIGN + " varchar, " +
          TranslationsTable.FILENAME + " varchar not null, " +
          TranslationsTable.URL + " varchar, " +
          TranslationsTable.LANGUAGE_CODE + " varchar, " +
          TranslationsTable.VERSION + " integer not null default 0," +
          TranslationsTable.MINIMUM_REQUIRED_VERSION + " integer not null default 0, " +
          TranslationsTable.DISPLAY_ORDER + " integer not null default -1 " +
          ");"
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(CREATE_TRANSLATIONS_TABLE)
  }

  @Suppress("LocalVariableName")
  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 4) {
      // a new column is added and columns are re-arranged
      val BACKUP_TABLE = TranslationsTable.TABLE_NAME + "_backup"
      db.beginTransaction()
      try {
        db.execSQL("ALTER TABLE " + TranslationsTable.TABLE_NAME + " RENAME TO " + BACKUP_TABLE)
        db.execSQL(CREATE_TRANSLATIONS_TABLE)
        db.execSQL("INSERT INTO " + TranslationsTable.TABLE_NAME + " (" +
            TranslationsTable.ID + ", " +
            TranslationsTable.NAME + ", " +
            TranslationsTable.TRANSLATOR + ", " +
            (if (oldVersion < 2) "" else (TranslationsTable.TRANSLATOR_FOREIGN + ", ")) +
        TranslationsTable.FILENAME + ", " +
            TranslationsTable.URL + ", " +
            (if (oldVersion < 3) "" else (TranslationsTable.LANGUAGE_CODE + ",")) +
        TranslationsTable.VERSION + ", " +
            TranslationsTable.DISPLAY_ORDER + ") " +
            "SELECT " + TranslationsTable.ID + ", " +
            TranslationsTable.NAME + ", " +
            TranslationsTable.TRANSLATOR + ", " +
            (if (oldVersion < 2) "" else "translator_foreign, ") +
        TranslationsTable.FILENAME + ", " +
            TranslationsTable.URL + ", " +
            (if (oldVersion < 3) "" else (TranslationsTable.LANGUAGE_CODE + ",")) +
        TranslationsTable.VERSION + ", " +
            TranslationsTable.ID +
            " FROM " + BACKUP_TABLE)
        db.execSQL("DROP TABLE $BACKUP_TABLE")
        db.execSQL("UPDATE " + TranslationsTable.TABLE_NAME + " SET " +
            TranslationsTable.MINIMUM_REQUIRED_VERSION + " = 2")
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    } else if (oldVersion < 5) {
      // the v3 and below update also updates to v5.
      // this code is called for updating from v4.
      upgradeToV5(db)
    }
  }

  private fun upgradeToV5(db: SQLiteDatabase) {
    // Add display order column and add arbitrary order to existing translations
    db.beginTransaction()
    try {
      db.execSQL("ALTER TABLE "
          + TranslationsTable.TABLE_NAME
          + " ADD COLUMN "
          + TranslationsTable.DISPLAY_ORDER
          + " integer not null default -1"
      )

      // for now, set the order to be the translation id
      db.execSQL("UPDATE " + TranslationsTable.TABLE_NAME + " SET " +
          TranslationsTable.DISPLAY_ORDER + " = " + TranslationsTable.ID)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  internal object TranslationsTable {
    const val TABLE_NAME = "translations"
    const val ID = "id"
    const val NAME = "name"
    const val TRANSLATOR = "translator"
    const val TRANSLATOR_FOREIGN = "translatorForeign"
    const val FILENAME = "filename"
    const val URL = "url"
    const val LANGUAGE_CODE = "languageCode"
    const val VERSION = "version"
    const val MINIMUM_REQUIRED_VERSION = "minimumRequiredVersion"
    const val DISPLAY_ORDER = "userDisplayOrder"
  }
}
