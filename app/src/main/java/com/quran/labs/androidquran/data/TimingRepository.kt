package com.quran.labs.androidquran.data

import android.database.Cursor
import android.database.SQLException
import android.util.SparseLongArray
import androidx.collection.LruCache
import com.quran.labs.androidquran.database.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class TimingRepository @Inject constructor() {
  private val lruCache: LruCache<SuraEntry, SparseLongArray> = LruCache(3)

  suspend fun timings(databasePath: String, sura: Int): SparseLongArray {
    val entry = SuraEntry(databasePath, sura)
    var timings = lruCache[entry]
    if (timings == null) {
      timings = queryTimings(databasePath, sura)
      lruCache.put(entry, timings)
    }
    return timings
  }

  fun clear() {
    lruCache.evictAll()
  }

  private suspend fun queryTimings(databasePath: String, sura: Int): SparseLongArray {
    return withContext(Dispatchers.IO) {
      val db = SuraTimingDatabaseHandler.getDatabaseHandler(databasePath)

      val map = SparseLongArray()
      var cursor: Cursor? = null
      try {
        cursor = db.getAyahTimings(sura)
        if (cursor != null && cursor.moveToFirst()) {
          do {
            val ayah = cursor.getInt(1)
            val time = cursor.getLong(2)
            map.put(ayah, time)
          } while (cursor.moveToNext())
        }
      } catch (se: SQLException) {
        // don't crash the app if the database is corrupt
        Timber.e(se)
      } finally {
        closeCursor(cursor)
      }

      map
    }
  }

  private data class SuraEntry(val databasePath: String, val sura: Int)
}
