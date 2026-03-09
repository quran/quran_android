package com.quran.common.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchTextUtilArabicTest {

  @Test
  fun normalize_removesDiacritics() {
    // Bismillah with full tashkeel
    val input = "\u0628\u0650\u0633\u0652\u0645\u0650" // بِسْمِ
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0628\u0633\u0645") // بسم
  }

  @Test
  fun normalize_removesShadda() {
    // رَبِّ with shadda on ba
    val input = "\u0631\u064E\u0628\u0651\u0650" // رَبِّ
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0631\u0628") // رب
  }

  @Test
  fun normalize_removesTatweel() {
    val input = "\u0628\u0640\u0633\u0640\u0645" // بـسـم with tatweel
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0628\u0633\u0645") // بسم
  }

  @Test
  fun normalize_alifMaddaToAlif() {
    val input = "\u0622\u0645\u0646" // آمن
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0627\u0645\u0646") // امن
  }

  @Test
  fun normalize_alifHamzaAboveToAlif() {
    val input = "\u0623\u062D\u0645\u062F" // أحمد
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0627\u062D\u0645\u062F") // احمد
  }

  @Test
  fun normalize_alifHamzaBelowToAlif() {
    val input = "\u0625\u0633\u0644\u0627\u0645" // إسلام
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0627\u0633\u0644\u0627\u0645") // اسلام
  }

  @Test
  fun normalize_alifWaslaToAlif() {
    val input = "\u0671\u0644\u0644\u0647" // ٱلله
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0627\u0644\u0644\u0647") // الله
  }

  @Test
  fun normalize_taaMarbuttaToHaa() {
    val input = "\u0631\u062D\u0645\u0629" // رحمة
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0631\u062D\u0645\u0647") // رحمه
  }

  @Test
  fun normalize_alifMaksuraToYaa() {
    val input = "\u0639\u0644\u0649" // على
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0639\u0644\u064A") // علي
  }

  @Test
  fun normalize_wawHamzaToWaw() {
    val input = "\u0624\u0645\u0646" // ؤمن
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0648\u0645\u0646") // ومن
  }

  @Test
  fun normalize_yaaHamzaToYaa() {
    val input = "\u0626\u0645\u0627\u0646" // ئمان
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u064A\u0645\u0627\u0646") // يمان
  }

  @Test
  fun normalize_trimsWhitespace() {
    val input = "  \u0628\u0633\u0645  "
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0628\u0633\u0645")
  }

  @Test
  fun normalize_emptyString() {
    assertThat(SearchTextUtil.normalizeArabic("")).isEmpty()
  }

  @Test
  fun normalize_nonArabicPassesThrough() {
    val input = "hello world"
    assertThat(SearchTextUtil.normalizeArabic(input)).isEqualTo("hello world")
  }

  @Test
  fun normalize_mixedDiacriticsOnSingleChar() {
    // Fatha + shadda + kasra on a single character
    val input = "\u0628\u064E\u0651\u0650" // بَّ
    val result = SearchTextUtil.normalizeArabic(input)
    assertThat(result).isEqualTo("\u0628") // ب
  }

  @Test
  fun normalize_allHamzaVariantsInOneString() {
    // أ إ آ ٱ ؤ ئ
    val input = "\u0623\u0625\u0622\u0671\u0624\u0626"
    val result = SearchTextUtil.normalizeArabic(input)
    // ا ا ا ا و ي
    assertThat(result).isEqualTo("\u0627\u0627\u0627\u0627\u0648\u064A")
  }

  // tokenize tests

  @Test
  fun tokenize_splitsOnWhitespace() {
    val result = SearchTextUtil.tokenizeArabic("\u0628\u0633\u0645 \u0627\u0644\u0644\u0647")
    assertThat(result).containsExactly("\u0628\u0633\u0645", "\u0627\u0644\u0644\u0647")
  }

  @Test
  fun tokenize_handlesMultipleSpaces() {
    val result = SearchTextUtil.tokenizeArabic("\u0628\u0633\u0645   \u0627\u0644\u0644\u0647")
    assertThat(result).containsExactly("\u0628\u0633\u0645", "\u0627\u0644\u0644\u0647")
  }

  @Test
  fun tokenize_emptyString() {
    assertThat(SearchTextUtil.tokenizeArabic("")).isEmpty()
  }

  @Test
  fun tokenize_blankString() {
    assertThat(SearchTextUtil.tokenizeArabic("   ")).isEmpty()
  }

  @Test
  fun tokenize_singleWord() {
    val result = SearchTextUtil.tokenizeArabic("\u0628\u0633\u0645")
    assertThat(result).containsExactly("\u0628\u0633\u0645")
  }
}
