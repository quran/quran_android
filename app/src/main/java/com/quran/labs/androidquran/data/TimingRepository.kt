package com.quran.labs.androidquran.data

import android.database.Cursor
import android.database.SQLException
import android.util.SparseArray
import android.util.SparseLongArray
import androidx.collection.LruCache
import com.quran.labs.androidquran.dao.audio.SuraTimings
import com.quran.labs.androidquran.dao.audio.WordTiming
import com.quran.labs.androidquran.database.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler.TimingsTable
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class TimingRepository @Inject constructor() {
  private val lruCache: LruCache<SuraEntry, SuraTimings> = LruCache(3)

  suspend fun timings(databasePath: String, sura: Int): SuraTimings {
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

  private suspend fun queryTimings(databasePath: String, sura: Int): SuraTimings {
    return withContext(Dispatchers.IO) {
      val db = SuraTimingDatabaseHandler.getDatabaseHandler(databasePath)

      val map = SparseLongArray()
      val wordTimings = SparseArray<List<WordTiming>>()
      var cursor: Cursor? = null
      try {
        cursor = db.getAyahTimings(sura)
        if (cursor != null && cursor.moveToFirst()) {
          val haveWords = cursor.columnNames.contains(TimingsTable.COL_WORDS)
          do {
            val ayah = cursor.getInt(1)
            val time = cursor.getLong(2)
            map.put(ayah, time)

            if (haveWords) {
              val words = cursor.getString(3)
              val items = words.split(",")
                .mapNotNull { entry ->
                  val pieces = entry.split(":")
                  val ayah = pieces.getOrNull(0)?.toIntOrNull()
                  val startTime = pieces.getOrNull(1)?.toLongOrNull()
                  val endtTime = pieces.getOrNull(2)?.toLongOrNull()
                  if (ayah != null && startTime != null && endtTime != null) {
                    WordTiming(ayah, startTime, endtTime)
                  } else {
                    null
                  }
                }
              if (items.isNotEmpty()) {
                wordTimings.put(ayah, items)
              }
            }
          } while (cursor.moveToNext())
        }
      } catch (se: SQLException) {
        // don't crash the app if the database is corrupt
        Timber.e(se)
      } finally {
        closeCursor(cursor)
      }

      SuraTimings(map, wordTimings)
    }
  }

  private data class SuraEntry(val databasePath: String, val sura: Int)
}
