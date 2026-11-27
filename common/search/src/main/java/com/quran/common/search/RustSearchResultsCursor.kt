package com.quran.common.search

import android.database.AbstractCursor

/**
 * Zero-allocation-per-scan [android.database.Cursor] backed by a packed native Rust search result
 * buffer `[verseIndex, rowId, sura, ayah, ...]`.
 *
 * Verse text (`column == 3`) is fetched lazily from [RustArabicSearchEngine.getVerseText] only
 * when read, avoiding JNI string allocations for unread rows (e.g. suggestion caps).
 */
class RustSearchResultsCursor(
  private val columnNames: Array<String>,
  private val packedMatches: IntArray,
  val indexGeneration: Int
) : AbstractCursor() {
  private val rowCount: Int = packedMatches.size / RustArabicSearchEngine.MATCH_STRIDE

  override fun getCount(): Int = rowCount

  override fun getColumnNames(): Array<String> = columnNames

  /** Returns the native Rust arena index for the current cursor row. */
  fun getCurrentVerseIndex(): Int {
    checkPosition()
    return packedMatches[position * RustArabicSearchEngine.MATCH_STRIDE]
  }

  /** Returns the underlying packed `[verseIndex, rowId, sura, ayah, ...]` buffer. */
  fun packedMatchesBuffer(): IntArray = packedMatches

  override fun getString(column: Int): String? {
    checkPosition()
    val base = position * RustArabicSearchEngine.MATCH_STRIDE
    return when (column) {
      0 -> packedMatches[base + 1].toString()
      1 -> packedMatches[base + 2].toString()
      2 -> packedMatches[base + 3].toString()
      3 -> RustArabicSearchEngine.getVerseText(packedMatches[base])
      else -> throw IndexOutOfBoundsException("Invalid column index $column")
    }
  }

  override fun getShort(column: Int): Short = getInt(column).toShort()

  override fun getInt(column: Int): Int {
    checkPosition()
    val base = position * RustArabicSearchEngine.MATCH_STRIDE
    return when (column) {
      0 -> packedMatches[base + 1]
      1 -> packedMatches[base + 2]
      2 -> packedMatches[base + 3]
      3 -> getString(3)?.toInt() ?: 0
      else -> throw IndexOutOfBoundsException("Invalid column index $column")
    }
  }

  override fun getLong(column: Int): Long = getInt(column).toLong()

  override fun getFloat(column: Int): Float = getInt(column).toFloat()

  override fun getDouble(column: Int): Double = getInt(column).toDouble()

  override fun isNull(column: Int): Boolean {
    checkPosition()
    return column == 3 && getString(3) == null
  }
}
