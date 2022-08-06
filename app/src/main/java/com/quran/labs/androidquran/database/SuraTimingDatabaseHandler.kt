package com.quran.labs.androidquran.database

import android.database.Cursor
import android.database.DefaultDatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import timber.log.Timber
import java.io.File
import java.lang.Exception
import java.util.HashMap

class SuraTimingDatabaseHandler private constructor(path: String) {
  private var database: SQLiteDatabase? = null

  object TimingsTable {
    const val TABLE_NAME = "timings"
    const val COL_SURA = "sura"
    const val COL_AYAH = "ayah"
    const val COL_TIME = "time"
  }

  object PropertiesTable {
    const val TABLE_NAME = "properties"
    const val COL_PROPERTY = "property"
    const val COL_VALUE = "value"
  }

  companion object {
    private val databaseMap: MutableMap<String, SuraTimingDatabaseHandler> = HashMap()

    @JvmStatic
    @Synchronized
    fun getDatabaseHandler(path: String): SuraTimingDatabaseHandler {
      var handler = databaseMap[path]
      if (handler == null) {
        handler = SuraTimingDatabaseHandler(path)
        databaseMap[path] = handler
      }
      return handler
    }

    @Synchronized
    fun clearDatabaseHandlerIfExists(databasePath: String) {
      try {
        val handler = databaseMap.remove(databasePath)
        if (handler != null) {
          handler.database?.close()
          databaseMap.remove(databasePath)
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  init {
    Timber.d("opening gapless data file, %s", path)
    database = try {
      SQLiteDatabase.openDatabase(
        path, null,
        SQLiteDatabase.NO_LOCALIZED_COLLATORS, DefaultDatabaseErrorHandler()
      )
    } catch (sce: SQLiteDatabaseCorruptException) {
      Timber.d("database corrupted: %s", path)
      null
    } catch (se: SQLException) {
      Timber.d("database at $path ${if (File(path).exists()) "exists " else "doesn 't exist"}")
      Timber.e(se)
      null
    }
  }

  private fun validDatabase(): Boolean = database?.isOpen ?: false

  fun getAyahTimings(sura: Int): Cursor? {
    if (!validDatabase()) return null

    return try {
      database?.query(
        TimingsTable.TABLE_NAME, arrayOf(
          TimingsTable.COL_SURA,
          TimingsTable.COL_AYAH, TimingsTable.COL_TIME
        ),
        TimingsTable.COL_SURA + "=" + sura,
        null, null, null, TimingsTable.COL_AYAH + " ASC"
      )
    } catch (e: Exception) {
      null
    }
  }

  fun getVersion(): Int {
      if (!validDatabase()) {
        return -1
      }
      var cursor: Cursor? = null
      return try {
        cursor = database?.query(
          PropertiesTable.TABLE_NAME, arrayOf(PropertiesTable.COL_VALUE),
          PropertiesTable.COL_PROPERTY + "= 'version'", null, null, null, null
        )
        if (cursor != null && cursor.moveToFirst()) {
          cursor.getInt(0)
        } else {
          1
        }
      } catch (e: Exception) {
        1
      } finally {
        DatabaseUtils.closeCursor(cursor)
      }
    }
}
