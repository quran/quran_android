package com.quran.common.search

import android.database.AbstractCursor

/**
 * Compact unboxed [android.database.Cursor] for 4-column verse search results (`_id`, `sura`,
 * `ayah`, `text`). Avoids boxing three `Int` values per row and allocating `Object[]` row arrays
 * as [android.database.MatrixCursor] does.
 */
class SearchResultsCursor private constructor(
  private val columnNames: Array<String>,
  private val ids: IntArray,
  private val suras: IntArray,
  private val ayahs: IntArray,
  private val texts: Array<String?>,
  private val rowCount: Int
) : AbstractCursor() {

  override fun getCount(): Int = rowCount

  override fun getColumnNames(): Array<String> = columnNames

  override fun getString(column: Int): String? {
    checkPosition()
    return when (column) {
      0 -> ids[mPos].toString()
      1 -> suras[mPos].toString()
      2 -> ayahs[mPos].toString()
      3 -> texts[mPos]
      else -> throw IndexOutOfBoundsException("Invalid column index $column")
    }
  }

  override fun getShort(column: Int): Short = getInt(column).toShort()

  override fun getInt(column: Int): Int {
    checkPosition()
    return when (column) {
      0 -> ids[mPos]
      1 -> suras[mPos]
      2 -> ayahs[mPos]
      3 -> texts[mPos]?.toInt() ?: 0
      else -> throw IndexOutOfBoundsException("Invalid column index $column")
    }
  }

  override fun getLong(column: Int): Long = getInt(column).toLong()

  override fun getFloat(column: Int): Float = getInt(column).toFloat()

  override fun getDouble(column: Int): Double = getInt(column).toDouble()

  override fun isNull(column: Int): Boolean {
    checkPosition()
    return column == 3 && texts[mPos] == null
  }

  class Builder(
    private val columnNames: Array<String>,
    initialCapacity: Int = 128
  ) {
    private var capacity = initialCapacity.coerceAtLeast(8)
    private var ids = IntArray(capacity)
    private var suras = IntArray(capacity)
    private var ayahs = IntArray(capacity)
    private var texts = arrayOfNulls<String>(capacity)
    var size: Int = 0
      private set

    fun addRow(id: Int, sura: Int, ayah: Int, text: String) {
      if (size == capacity) {
        val newCapacity = capacity * 2
        ids = ids.copyOf(newCapacity)
        suras = suras.copyOf(newCapacity)
        ayahs = ayahs.copyOf(newCapacity)
        texts = texts.copyOf(newCapacity)
        capacity = newCapacity
      }
      ids[size] = id
      suras[size] = sura
      ayahs[size] = ayah
      texts[size] = text
      size++
    }

    fun build(): SearchResultsCursor {
      return SearchResultsCursor(columnNames, ids, suras, ayahs, texts, size)
    }
  }
}
