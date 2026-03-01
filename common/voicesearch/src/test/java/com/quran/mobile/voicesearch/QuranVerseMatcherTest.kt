package com.quran.mobile.voicesearch

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.SearchTextUtil
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QuranVerseMatcherTest {

  private lateinit var matcher: QuranVerseMatcher

  // Sample verses (pre-normalized) for testing
  private val testVerses = listOf(
    indexedVerse(1, 1, "\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0651\u064E\u0647\u0650 \u0627\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0670\u0646\u0650 \u0627\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650"),
    // بسم الله الرحمن الرحيم
    indexedVerse(1, 2, "\u0627\u0644\u0652\u062D\u064E\u0645\u0652\u062F\u064F \u0644\u0650\u0644\u0651\u064E\u0647\u0650 \u0631\u064E\u0628\u0651\u0650 \u0627\u0644\u0652\u0639\u064E\u0627\u0644\u064E\u0645\u0650\u064A\u0646\u064E"),
    // الحمد لله رب العالمين
    indexedVerse(1, 3, "\u0627\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0670\u0646\u0650 \u0627\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650"),
    // الرحمن الرحيم
    indexedVerse(1, 4, "\u0645\u064E\u0627\u0644\u0650\u0643\u0650 \u064A\u064E\u0648\u0652\u0645\u0650 \u0627\u0644\u062F\u0651\u0650\u064A\u0646\u0650"),
    // مالك يوم الدين
    indexedVerse(2, 255,
      "\u0627\u0644\u0644\u0651\u064E\u0647\u064F \u0644\u064E\u0627 \u0625\u0650\u0644\u064E\u0670\u0647\u064E \u0625\u0650\u0644\u0651\u064E\u0627 \u0647\u064F\u0648\u064E \u0627\u0644\u0652\u062D\u064E\u064A\u0651\u064F \u0627\u0644\u0652\u0642\u064E\u064A\u0651\u064F\u0648\u0645\u064F"),
    // الله لا إله إلا هو الحي القيوم (beginning of Ayat al-Kursi)
  )

  @Before
  fun setUp() {
    matcher = QuranVerseMatcher(testVerses)
  }

  @Test
  fun match_exactSubstring_returnsExactMatchType() = runTest {
    // Search for "بسم الله" (bismillah) - normalized
    val query = "\u0628\u0633\u0645 \u0627\u0644\u0644\u0647"
    val results = matcher.match(query)

    assertThat(results).isNotEmpty()
    assertThat(results[0].sura).isEqualTo(1)
    assertThat(results[0].ayah).isEqualTo(1)
    assertThat(results[0].matchType).isEqualTo(MatchType.EXACT)
  }

  @Test
  fun match_exactSubstring_scoresHigherForBetterCoverage() = runTest {
    // "الرحمن الرحيم" matches both 1:1 (partial) and 1:3 (full verse)
    val query = "\u0627\u0644\u0631\u062D\u0645\u0646 \u0627\u0644\u0631\u062D\u064A\u0645"
    val results = matcher.match(query)

    assertThat(results).isNotEmpty()
    // 1:3 should score higher because query covers more of the verse
    val verse13 = results.find { it.sura == 1 && it.ayah == 3 }
    val verse11 = results.find { it.sura == 1 && it.ayah == 1 }
    assertThat(verse13).isNotNull()
    assertThat(verse11).isNotNull()
    assertThat(verse13!!.score).isGreaterThan(verse11!!.score)
  }

  @Test
  fun match_wordOverlap_returnsResults() = runTest {
    // Search with some matching words but not an exact substring
    val query = "\u0627\u0644\u062D\u0645\u062F \u0627\u0644\u0639\u0627\u0644\u0645\u064A\u0646"
    // "الحمد العالمين" - words from 1:2 but not consecutive
    val results = matcher.match(query)

    assertThat(results).isNotEmpty()
    val verse12 = results.find { it.sura == 1 && it.ayah == 2 }
    assertThat(verse12).isNotNull()
  }

  @Test
  fun match_emptyQuery_returnsEmpty() = runTest {
    val results = matcher.match("")
    assertThat(results).isEmpty()
  }

  @Test
  fun match_blankQuery_returnsEmpty() = runTest {
    val results = matcher.match("   ")
    assertThat(results).isEmpty()
  }

  @Test
  fun match_nonArabicQuery_returnsEmpty() = runTest {
    val results = matcher.match("hello world")
    assertThat(results).isEmpty()
  }

  @Test
  fun match_respectsLimit() = runTest {
    val results = matcher.match("\u0627\u0644\u0631\u062D\u064A\u0645", limit = 2)
    // "الرحيم" appears in multiple verses
    assertThat(results.size).isAtMost(2)
  }

  @Test
  fun match_resultsAreSortedByScoreDescending() = runTest {
    val query = "\u0627\u0644\u0631\u062D\u0645\u0646 \u0627\u0644\u0631\u062D\u064A\u0645"
    val results = matcher.match(query)

    for (i in 0 until results.size - 1) {
      assertThat(results[i].score).isAtLeast(results[i + 1].score)
    }
  }

  @Test
  fun match_withDiacritics_stillMatches() = runTest {
    // Query with full diacritics should still match after normalization
    val query = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0651\u064E\u0647\u0650"
    // بِسْمِ اللَّهِ
    val results = matcher.match(query)

    assertThat(results).isNotEmpty()
    assertThat(results[0].sura).isEqualTo(1)
    assertThat(results[0].ayah).isEqualTo(1)
  }

  @Test
  fun match_emptyVerseList_returnsEmpty() = runTest {
    val emptyMatcher = QuranVerseMatcher(emptyList())
    val results = emptyMatcher.match("\u0628\u0633\u0645")
    assertThat(results).isEmpty()
  }

  // Levenshtein distance tests

  @Test
  fun levenshteinDistance_identicalStrings() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abc", "abc")).isEqualTo(0)
  }

  @Test
  fun levenshteinDistance_emptyStrings() {
    assertThat(QuranVerseMatcher.levenshteinDistance("", "")).isEqualTo(0)
  }

  @Test
  fun levenshteinDistance_oneEmpty() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abc", "")).isEqualTo(3)
    assertThat(QuranVerseMatcher.levenshteinDistance("", "abc")).isEqualTo(3)
  }

  @Test
  fun levenshteinDistance_singleInsertion() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abc", "abdc")).isEqualTo(1)
  }

  @Test
  fun levenshteinDistance_singleDeletion() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abdc", "abc")).isEqualTo(1)
  }

  @Test
  fun levenshteinDistance_singleSubstitution() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abc", "axc")).isEqualTo(1)
  }

  @Test
  fun levenshteinDistance_completelyDifferent() {
    assertThat(QuranVerseMatcher.levenshteinDistance("abc", "xyz")).isEqualTo(3)
  }

  @Test
  fun levenshteinDistance_arabicWords() {
    // Two similar Arabic words
    val dist = QuranVerseMatcher.levenshteinDistance(
      "\u0631\u062D\u0645\u0646", // رحمن
      "\u0631\u062D\u064A\u0645"  // رحيم
    )
    assertThat(dist).isEqualTo(2)
  }

  // Helper to create IndexedVerse from raw text (with diacritics)
  private fun indexedVerse(sura: Int, ayah: Int, rawText: String): IndexedVerse {
    val normalized = SearchTextUtil.normalizeArabic(rawText)
    return IndexedVerse(
      sura = sura,
      ayah = ayah,
      rawText = rawText,
      normalizedText = normalized,
      words = SearchTextUtil.tokenizeArabic(normalized)
    )
  }
}
