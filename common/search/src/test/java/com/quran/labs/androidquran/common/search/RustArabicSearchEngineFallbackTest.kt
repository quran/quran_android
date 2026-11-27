package com.quran.labs.androidquran.common.search

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.RustSearchResultsCursor
import org.junit.Test

class RustArabicSearchEngineFallbackTest {

  @Test
  fun `host JVM fallback reports native unavailable and safely returns null or no-op`() {
    assertThat(RustArabicSearchEngine.isNativeAvailable).isFalse()
    assertThat(RustArabicSearchEngine.isLoaded()).isFalse()
    assertThat(RustArabicSearchEngine.search("الله", 10, 1)).isNull()
    assertThat(RustArabicSearchEngine.getVerseText(0)).isNull()
    assertThat(RustArabicSearchEngine.findMatchSpans(0, "الله", 1)).isNull()

    val genBefore = RustArabicSearchEngine.generation
    RustArabicSearchEngine.releaseIndex()
    assertThat(RustArabicSearchEngine.generation).isNotEqualTo(genBefore)
  }

  @Test
  fun `RustSearchResultsCursor computes count and generation without Android runtime`() {
    val columns = arrayOf("_id", "sura", "ayah", "text")
    val packed = intArrayOf(
      0, 1, 1, 1,
      254, 262, 2, 255
    )
    val cursor = RustSearchResultsCursor(columns, packed, indexGeneration = 7)
    assertThat(cursor.count).isEqualTo(2)
    assertThat(cursor.indexGeneration).isEqualTo(7)
    assertThat(cursor.columnNames).isEqualTo(columns)
  }
}
