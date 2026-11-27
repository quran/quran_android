package com.quran.common.search

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import timber.log.Timber
import java.util.Locale

/**
 * JNI bridge to the native Rust Arabic Quran search engine (`libquran_search.so`).
 *
 * Maintains a thread-safe (`RwLock`) contiguous UTF-16 verse arena in native memory (~0.9 MB for
 * all 6,236 Quran verses), loaded once on first Arabic search and released on memory pressure via
 * [releaseIndex].
 *
 * If the native library cannot be loaded (e.g., host JVM / Robolectric unit tests),
 * [isNativeAvailable] is `false` and callers transparently fall back to pure-Kotlin search.
 */
object RustArabicSearchEngine {
  /** Packed int stride returned per hit by [nativeSearch]: `[verseIndex, rowId, sura, ayah]`. */
  const val MATCH_STRIDE = 4

  private const val LOG_TAG = "RustArabicSearch"
  private const val DEFAULT_INITIAL_ROWS = 6236
  private val VALID_TABLE_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

  val isNativeAvailable: Boolean = try {
    System.loadLibrary("quran_search")
    true
  } catch (_: UnsatisfiedLinkError) {
    false
  } catch (_: SecurityException) {
    false
  }

  private val indexLock = Any()

  @Volatile
  private var loadedKey: String? = null

  @Volatile
  var generation: Int = 0
    private set

  @JvmStatic
  external fun nativeLoadIndex(
    rowIds: IntArray,
    suras: IntArray,
    ayahs: IntArray,
    texts: Array<String>
  ): Boolean

  @JvmStatic
  external fun nativeIsLoaded(): Boolean

  @JvmStatic
  external fun nativeSearch(query: String, maxResults: Int): IntArray?

  @JvmStatic
  external fun nativeGetVerseText(verseIndex: Int): String?

  @JvmStatic
  external fun nativeFindMatchSpans(verseIndex: Int, query: String): IntArray?

  @JvmStatic
  external fun nativeReleaseIndex()

  fun isLoaded(): Boolean = isNativeAvailable && nativeIsLoaded()

  /**
   * Ensures the Rust verse arena is loaded for `(database.path, table)` and returns the active
   * index generation (`> 0`), or `-1` if native search is unavailable or loading fails.
   */
  fun ensureLoaded(database: SQLiteDatabase, table: String): Int {
    if (!isNativeAvailable || !VALID_TABLE_NAME.matches(table)) {
      return -1
    }
    val key = buildDbKey(database, table)
    val currentGen = generation
    if (loadedKey == key && currentGen > 0 && nativeIsLoaded()) {
      return currentGen
    }

    synchronized(indexLock) {
      val recheckGen = generation
      if (loadedKey == key && recheckGen > 0 && nativeIsLoaded()) {
        return recheckGen
      }

      val startNs = System.nanoTime()
      val loaded = loadFromDatabase(database, table)
      if (!loaded) {
        return -1
      }
      val newGen = (generation + 1).let { if (it <= 0) 1 else it }
      loadedKey = key
      generation = newGen
      val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
      logTiming("RustArabicSearch: index loaded in %.2f ms", elapsedMs)
      return newGen
    }
  }

  /**
   * Runs a full-index Arabic search in native Rust and logs timing without leaking the query.
   * Returns packed `[verseIndex, rowId, sura, ayah, ...]`, or `null` if unavailable/unloaded.
   */
  fun search(query: String, maxResults: Int, expectedGeneration: Int): IntArray? {
    if (!isNativeAvailable || maxResults <= 0 || expectedGeneration <= 0 || generation != expectedGeneration) {
      return null
    }
    val startNs = System.nanoTime()
    val packed = nativeSearch(query, maxResults) ?: return null
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
    if (generation != expectedGeneration) {
      return null
    }
    val count = packed.size / MATCH_STRIDE
    logTiming("RustArabicSearch: %d hits in %.2f ms", count, elapsedMs)
    return packed
  }

  fun getVerseText(verseIndex: Int): String? {
    if (!isNativeAvailable || verseIndex < 0) {
      return null
    }
    return nativeGetVerseText(verseIndex)
  }

  /**
   * Returns packed UTF-16 match spans `[start0, end0, start1, end1, ...]` for [verseIndex],
   * or `null` if native is unavailable or the index generation changed concurrently.
   */
  fun findMatchSpans(verseIndex: Int, query: String, expectedGeneration: Int): IntArray? {
    if (!isNativeAvailable || verseIndex < 0 || expectedGeneration <= 0 || generation != expectedGeneration) {
      return null
    }
    val spans = nativeFindMatchSpans(verseIndex, query) ?: return null
    if (generation != expectedGeneration) {
      return null
    }
    return spans
  }

  /**
   * Releases the in-memory native verse arena (hooked into `onTrimMemory`/`onLowMemory` and
   * Arabic database invalidation).
   */
  fun releaseIndex() {
    synchronized(indexLock) {
      loadedKey = null
      generation = (generation + 1).let { if (it <= 0) 1 else it }
      if (isNativeAvailable) {
        nativeReleaseIndex()
      }
    }
  }

  private fun buildDbKey(database: SQLiteDatabase, table: String): String {
    return "${database.path}|$table"
  }

  private fun loadFromDatabase(database: SQLiteDatabase, table: String): Boolean {
    return try {
      database.rawQuery("SELECT rowid, sura, ayah, text FROM $table", null).use { cursor ->
        val initialCap = cursor.count.takeIf { it > 0 } ?: DEFAULT_INITIAL_ROWS
        var capacity = initialCap
        var rowIds = IntArray(capacity)
        var suras = IntArray(capacity)
        var ayahs = IntArray(capacity)
        var texts = arrayOfNulls<String>(capacity)
        var size = 0

        while (cursor.moveToNext()) {
          if (size == capacity) {
            val newCapacity = capacity * 2
            rowIds = rowIds.copyOf(newCapacity)
            suras = suras.copyOf(newCapacity)
            ayahs = ayahs.copyOf(newCapacity)
            texts = texts.copyOf(newCapacity)
            capacity = newCapacity
          }
          rowIds[size] = cursor.getInt(0)
          suras[size] = cursor.getInt(1)
          ayahs[size] = cursor.getInt(2)
          texts[size] = cursor.getString(3) ?: ""
          size++
        }

        val finalRowIds = if (size == rowIds.size) rowIds else rowIds.copyOf(size)
        val finalSuras = if (size == suras.size) suras else suras.copyOf(size)
        val finalAyahs = if (size == ayahs.size) ayahs else ayahs.copyOf(size)
        val finalTexts = Array(size) { i -> texts[i] ?: "" }
        nativeLoadIndex(finalRowIds, finalSuras, finalAyahs, finalTexts)
      }
    } catch (e: Exception) {
      Timber.e(e, "RustArabicSearch: failed loading Arabic verse index")
      false
    }
  }

  private fun logTiming(format: String, vararg args: Any) {
    Timber.i(format, *args)
    val hasDebugTree = Timber.forest().any { it is Timber.DebugTree }
    if (!hasDebugTree) {
      Log.i(LOG_TAG, String.format(Locale.US, format, *args))
    }
  }
}
