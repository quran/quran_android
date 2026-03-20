package com.quran.labs.androidquran.test.helpers

import android.database.Cursor
import android.database.MatrixCursor
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.database.DatabaseHandler
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when` as whenever

/**
 * Test helper utilities for mocking DatabaseHandler behavior.
 *
 * These helpers reduce boilerplate when setting up DatabaseHandler mocks in unit tests.
 *
 * Usage:
 * ```kotlin
 * @Mock lateinit var databaseHandler: DatabaseHandler
 *
 * @Before
 * fun setup() {
 *   databaseHandler.mockValidDatabase(true)
 *   databaseHandler.mockGetVerses(listOf(
 *     QuranText(1, 1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", null)
 *   ))
 * }
 * ```
 */

/**
 * Mock validDatabase() to return a specific value.
 */
fun DatabaseHandler.mockValidDatabase(isValid: Boolean = true) {
  whenever(this.validDatabase()).thenReturn(isValid)
}

/**
 * Mock getSchemaVersion() to return a specific value.
 */
fun DatabaseHandler.mockSchemaVersion(version: Int = 1) {
  whenever(this.getSchemaVersion()).thenReturn(version)
}

/**
 * Mock getTextVersion() to return a specific value.
 */
fun DatabaseHandler.mockTextVersion(version: Int = 1) {
  whenever(this.getTextVersion()).thenReturn(version)
}

/**
 * Mock getVerses() to return a cursor with specific verse data.
 *
 * Example:
 * ```kotlin
 * handler.mockGetVersesCursor(
 *   listOf(Triple(1, 1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"))
 * )
 * ```
 */
fun DatabaseHandler.mockGetVersesCursor(verses: List<Triple<Int, Int, String>>) {
  val cursor = createVerseCursor(verses)
  whenever(this.getVerses(anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
    .thenReturn(cursor)
}

/**
 * Mock getVersesByIds() to return a cursor with specific verse data.
 *
 * Example:
 * ```kotlin
 * handler.mockGetVersesByIds(
 *   listOf(Triple(1, 1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"))
 * )
 * ```
 */
fun DatabaseHandler.mockGetVersesByIds(verses: List<Triple<Int, Int, String>>) {
  val cursor = createVerseCursor(verses)
  whenever(this.getVersesByIds(any<List<Int>>()))
    .thenReturn(cursor)
}

/**
 * Mock search() to return a cursor with specific verse data.
 */
fun DatabaseHandler.mockSearch(verses: List<Triple<Int, Int, String>>) {
  val cursor = createVerseCursor(verses)
  whenever(this.search(anyString(), any(), any()))
    .thenReturn(cursor)
  whenever(this.search(anyString(), anyString(), any(), any()))
    .thenReturn(cursor)
}

/**
 * Helper to create a Cursor from verse data.
 *
 * Returns a MatrixCursor with columns: _id, sura, ayah, text
 */
private fun createVerseCursor(verses: List<Triple<Int, Int, String>>): Cursor {
  val cursor = MatrixCursor(arrayOf("_id", "sura", "ayah", "text"))
  verses.forEachIndexed { index, (sura, ayah, text) ->
    cursor.addRow(arrayOf<Any>(index + 1, sura, ayah, text))
  }
  return cursor
}

/**
 * Create test verse data for common suras.
 *
 * Example:
 * ```kotlin
 * val fatihaVerses = createFatihaVerses()
 * handler.mockGetVersesCursor(fatihaVerses)
 * ```
 */
fun createFatihaVerses(): List<Triple<Int, Int, String>> {
  return listOf(
    Triple(1, 1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"),
    Triple(1, 2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"),
    Triple(1, 3, "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
    Triple(1, 4, "مَٰلِكِ يَوْمِ ٱلدِّينِ"),
    Triple(1, 5, "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ"),
    Triple(1, 6, "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ"),
    Triple(1, 7, "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ ٱلْمَغْضُوبِ عَلَيْهِمْ وَلَا ٱلضَّآلِّينَ")
  )
}

/**
 * Create test verse data with a simple pattern for testing.
 *
 * Example:
 * ```kotlin
 * val testVerses = createTestVerses(sura = 2, count = 5)
 * // Creates verses: (2,1,"verse 1"), (2,2,"verse 2"), ...
 * ```
 */
fun createTestVerses(sura: Int, startAyah: Int = 1, count: Int): List<Triple<Int, Int, String>> {
  return (startAyah until startAyah + count).map { ayah: Int ->
    Triple<Int, Int, String>(sura, ayah, "verse $ayah")
  }
}
