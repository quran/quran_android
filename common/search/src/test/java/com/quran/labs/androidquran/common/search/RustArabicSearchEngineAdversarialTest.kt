package com.quran.labs.androidquran.common.search

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.RustSearchResultsCursor
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.common.search.arabic.ArabicMorphologicalQuery
import org.junit.Assert.assertThrows
import org.junit.Test

class RustArabicSearchEngineAdversarialTest {

  @Test
  fun `ArabicCompiledQuery handles empty, parens, surrogate emoji, tatweel, ZWJ, and oversized queries`() {
    assertThat(ArabicCompiledQuery.compile("")).isNull()
    assertThat(ArabicCompiledQuery.compile("()")).isNull()
    assertThat(ArabicCompiledQuery.compile("((()))")).isNull()
    // UTF-16 surrogates (emoji) must return null so caller falls back safely
    assertThat(ArabicCompiledQuery.compile("🕌الله")).isNull()

    val nonMatchingNonNull = listOf(
      "   ",
      "\u0640\u0640",
      "\u200B\u200C\u200D\u200F",
      "ا".repeat(512)
    )
    val verse = "بسم الله الرحمن الرحيم"
    val sb = StringBuilder()
    for (q in nonMatchingNonNull) {
      val compiled = ArabicCompiledQuery.compile(q)
      assertThat(compiled).isNotNull()
      assertThat(compiled!!.findFirst(verse, 0)).isEqualTo(-1)
      assertThat(compiled.highlightTo(verse, "<b>", "</b>", sb)).isNull()
    }

    val matched = ArabicCompiledQuery.compile("الله")!!
    assertThat(matched.findFirst(verse, -5)).isEqualTo(4)
    assertThat(matched.highlightTo(verse, "<b>", "</b>", sb))
      .isEqualTo("بسم <b>الله</b> الرحمن الرحيم")

    // Adversarial inputs to ArabicMorphologicalQuery must never throw or falsely match
    assertThat(ArabicMorphologicalQuery.compile("")).isNull()
    assertThat(ArabicMorphologicalQuery.compile("()")).isNull()
    assertThat(ArabicMorphologicalQuery.compile("((()))")).isNull()
    for (q in listOf("   ", "\u0640\u0640", "\u200B\u200C\u200D\u200F", "🕌الله", "ا".repeat(512), ".*+?[\\]{}|")) {
      val mq = ArabicMorphologicalQuery.compile(q)
      assertThat(mq).isNotNull()
      assertThat(mq!!.findAllSpans(verse)).isEmpty()
    }
    // Tatweel and tashkeel inside Arabic query normalize cleanly and match verse
    assertThat(ArabicMorphologicalQuery.compile("ال\u0640ل\u0640ه")!!.findAllSpans(verse)).asList().containsExactly(4, 8)
    assertThat(ArabicMorphologicalQuery.compile("اللَّهِ")!!.findAllSpans("الحمد لله رب العالمين")).asList().containsExactly(6, 9)
  }

  @Test
  fun `RustSearchResultsCursor handles empty, partial-stride, typed column reads, and out-of-bounds columns`() {
    val emptyCursor = RustSearchResultsCursor(
      arrayOf("_id", "sura", "ayah", "text"),
      intArrayOf(),
      indexGeneration = 1
    )
    assertThat(emptyCursor.count).isEqualTo(0)
    assertThat(emptyCursor.packedMatchesBuffer()).isEmpty()

    // Partial trailing elements (< MATCH_STRIDE=4) are safely truncated by integer division
    val partialCursor = RustSearchResultsCursor(
      arrayOf("_id", "sura", "ayah", "text"),
      intArrayOf(1, 2, 3),
      indexGeneration = 2
    )
    assertThat(partialCursor.count).isEqualTo(0)

    val packed = intArrayOf(7, 42, 2, 255)
    val cursor = RustSearchResultsCursor(
      arrayOf("_id", "sura", "ayah", "text"),
      packed,
      indexGeneration = 9
    )
    assertThat(cursor.count).isEqualTo(1)
    assertThat(cursor.indexGeneration).isEqualTo(9)
    assertThat(cursor.packedMatchesBuffer()).isSameInstanceAs(packed)
    assertThat(cursor.getCurrentVerseIndex()).isEqualTo(7)
    assertThat(cursor.getInt(0)).isEqualTo(42)
    assertThat(cursor.getInt(1)).isEqualTo(2)
    assertThat(cursor.getInt(2)).isEqualTo(255)
    assertThat(cursor.getShort(1)).isEqualTo(2.toShort())
    assertThat(cursor.getLong(2)).isEqualTo(255L)
    assertThat(cursor.getFloat(1)).isEqualTo(2f)
    assertThat(cursor.getDouble(2)).isEqualTo(255.0)
    assertThat(cursor.getString(0)).isEqualTo("42")
    assertThat(cursor.getString(1)).isEqualTo("2")
    assertThat(cursor.getString(2)).isEqualTo("255")
    assertThat(cursor.isNull(0)).isFalse()

    assertThrows(IndexOutOfBoundsException::class.java) { cursor.getInt(-1) }
    assertThrows(IndexOutOfBoundsException::class.java) { cursor.getString(4) }
    assertThat(cursor.getString(3)).isNull() // JNI unavailable on host JVM
    assertThat(cursor.isNull(3)).isTrue()
  }

  @Test
  fun `RustArabicSearchEngine rejects invalid arguments and increments generation monotonically`() {
    assertThat(RustArabicSearchEngine.isNativeAvailable).isFalse()
    val g0 = RustArabicSearchEngine.generation
    RustArabicSearchEngine.releaseIndex()
    val g1 = RustArabicSearchEngine.generation
    RustArabicSearchEngine.releaseIndex()
    val g2 = RustArabicSearchEngine.generation
    assertThat(g1).isGreaterThan(g0)
    assertThat(g2).isGreaterThan(g1)

    assertThat(RustArabicSearchEngine.search("الله", maxResults = 0, expectedGeneration = g2)).isNull()
    assertThat(RustArabicSearchEngine.search("الله", maxResults = 10, expectedGeneration = 0)).isNull()
    assertThat(RustArabicSearchEngine.search("الله", maxResults = 10, expectedGeneration = -1)).isNull()
    assertThat(RustArabicSearchEngine.getVerseText(-1)).isNull()
    assertThat(RustArabicSearchEngine.findMatchSpans(-1, "الله", expectedGeneration = g2)).isNull()
    assertThat(RustArabicSearchEngine.findMatchSpans(0, "الله", expectedGeneration = -1)).isNull()
  }
}
