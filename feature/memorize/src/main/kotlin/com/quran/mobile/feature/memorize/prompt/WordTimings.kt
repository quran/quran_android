package com.quran.mobile.feature.memorize.prompt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.quran.mobile.feature.memorize.data.BundledAssets
import com.quran.mobile.feature.memorize.tracker.WordId
import java.io.File
import java.io.IOException

/**
 * Word timings of one reciter's gapless sura files (tools/memorize/align_timing_pack.py): where
 * each word starts and ends, and which exact files (by size) they were measured on.
 */
class WordTimings private constructor(private val database: SQLiteDatabase) : AutoCloseable {

  data class Span(val startMs: Long, val endMs: Long)

  /** The size of the sura file the timings were made for, or null if the sura isn't covered. */
  fun expectedFileSize(sura: Int): Long? =
    database.rawQuery("SELECT size FROM audio_files WHERE sura = ?", arrayOf(sura.toString())).use {
      if (it.moveToFirst()) it.getLong(0) else null
    }

  /** The audio span of [words] (consecutive words of one sura), or null if any is unknown. */
  fun span(words: List<WordId>): Span? {
    if (words.isEmpty()) return null
    val sura = words.first().sura
    if (words.any { it.sura != sura }) return null
    val spans = words.map { word(it) ?: return null }
    return Span(spans.minOf { it.startMs }, spans.maxOf { it.endMs })
  }

  private fun word(id: WordId): Span? {
    val words = database.rawQuery(
      "SELECT words FROM timings WHERE sura = ? AND ayah = ?",
      arrayOf(id.sura.toString(), id.ayah.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
    // "word:start:end,word:start:end,..."
    for (entry in words.split(',')) {
      val parts = entry.split(':')
      if (parts.size == 3 && parts[0].toIntOrNull() == id.word) {
        return Span(parts[1].toLong(), parts[2].toLong())
      }
    }
    return null
  }

  override fun close() = database.close()

  companion object {
    fun fromFile(file: File): WordTimings =
      WordTimings(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))

    /** Opens timings bundled as an asset, or null if they aren't bundled or can't be opened. */
    fun fromAsset(context: Context, asset: String): WordTimings? = try {
      fromFile(BundledAssets.extract(context, asset))
    } catch (_: IOException) {
      null
    } catch (_: SQLiteException) {
      null
    }
  }
}
