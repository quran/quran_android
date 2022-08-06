package com.quran.labs.androidquran.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.SparseArray

import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.database.TranslationsDBHelper.TranslationsTable
import com.quran.labs.androidquran.util.QuranFileUtils

import java.util.ArrayList
import java.util.Collections
import timber.log.Timber

import androidx.annotation.WorkerThread
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationsDBAdapter @Inject constructor(
  private val context: Context,
  adapter: TranslationsDBHelper,
  private val quranFileUtils: QuranFileUtils
) {
  private val db: SQLiteDatabase = adapter.writableDatabase

  @Volatile
  private var cachedTranslations: List<LocalTranslation>? = null

  var lastWriteTime: Long = 0
    private set

  fun getTranslationsHash(): SparseArray<LocalTranslation> {
    val result = SparseArray<LocalTranslation>()
    for (item in getTranslations()) {
      result.put(item.id, item)
    }
    return result
  }

  // intentional, since cachedTranslations can be replaced by another thread, causing the check
  // to be true, but the cached object returned to be null (or to change).
  @WorkerThread
  fun getTranslations(): List<LocalTranslation> {
    // intentional, since cachedTranslations can be replaced by another thread, causing the check
    // to be true, but the cached object returned to be null (or to change).
    val cached = cachedTranslations
    if (!cached.isNullOrEmpty()) {
      return cached
    }
    var items: MutableList<LocalTranslation> = ArrayList()
    var cursor: Cursor? = null
    try {
      cursor = db.query(TranslationsTable.TABLE_NAME,
        null, null, null, null, null,
        TranslationsTable.ID + " ASC")

      while (cursor.moveToNext()) {
        val id = cursor.getInt(0)
        val name = cursor.getString(1)
        val translator = cursor.getString(2)
        val translatorForeign = cursor.getString(3)
        val filename = cursor.getString(4)
        val url = cursor.getString(5)
        val languageCode = cursor.getString(6)
        val version = cursor.getInt(7)
        val minimumVersion = cursor.getInt(8)
        val displayOrder = cursor.getInt(9)

        if (quranFileUtils.hasTranslation(context, filename)) {
          items.add(
            LocalTranslation(
              id, filename, name, translator,
              translatorForeign, url, languageCode, version, minimumVersion, displayOrder
            )
          )
        }
      }
    } finally {
      cursor?.close()
    }

    items = Collections.unmodifiableList(items)
    if (items.size > 0) {
      cachedTranslations = items
    }
    return items
  }

  fun deleteTranslationByFile(filename: String) {
    db.execSQL("DELETE FROM " + TranslationsTable.TABLE_NAME + " WHERE " +
          TranslationsTable.FILENAME + " = ?", arrayOf<Any>(filename))
  }

  fun writeTranslationUpdates(updates: List<TranslationItem>): Boolean {
    var result = true
    db.beginTransaction()

    try {
      var cachedNextOrder = -1
      for (item in updates) {
        if (item.exists()) {
          var displayOrder = 0
          val translation = item.translation
          if (item.displayOrder > -1) {
            displayOrder = item.displayOrder
          } else {
            var cursor: Cursor? = null
            if (cachedNextOrder == -1) {
              try {
                // get next highest display order
                cursor = db.query(
                  TranslationsTable.TABLE_NAME, arrayOf(TranslationsTable.DISPLAY_ORDER),
                  null, null, null, null,
                  TranslationsTable.DISPLAY_ORDER + " DESC",
                  "1"
                )
                if (cursor != null && cursor.moveToFirst()) {
                  cachedNextOrder = cursor.getInt(0) + 1
                  displayOrder = cachedNextOrder++
                }
              } finally {
                cursor?.close()
              }
            } else {
              displayOrder = cachedNextOrder++
            }
          }

          val values = ContentValues()
          values.put(TranslationsTable.ID, translation.id)
          values.put(TranslationsTable.NAME, translation.displayName)
          values.put(TranslationsTable.TRANSLATOR, translation.translator)
          values.put(TranslationsTable.TRANSLATOR_FOREIGN,
            translation.translatorNameLocalized)
          values.put(TranslationsTable.FILENAME, translation.fileName)
          values.put(TranslationsTable.URL, translation.fileUrl)
          values.put(TranslationsTable.LANGUAGE_CODE, translation.languageCode)
          values.put(TranslationsTable.VERSION, item.localVersion)
          values.put(TranslationsTable.MINIMUM_REQUIRED_VERSION, translation.minimumVersion)
          values.put(TranslationsTable.DISPLAY_ORDER, displayOrder)

          db.replace(TranslationsTable.TABLE_NAME, null, values)
        } else {
          db.delete(TranslationsTable.TABLE_NAME,
            TranslationsTable.ID + " = " + item.translation.id, null)
        }
      }
      db.setTransactionSuccessful()

      lastWriteTime = System.currentTimeMillis()
      // clear the cached translations
      cachedTranslations = null
    } catch (e: Exception) {
      result = false
      Timber.d(e, "error writing translation updates")
    } finally {
      db.endTransaction()
    }

    return result
  }
}
