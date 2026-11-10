package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranUtilsTest {

  @Test
  fun testNullStringIsNotArabic() {
    assertThat(QuranUtils.doesStringContainArabic(null)).isFalse()
  }

  @Test
  fun testEmptyStringIsNotArabic() {
    assertThat(QuranUtils.doesStringContainArabic("")).isFalse()
  }

  @Test
  fun testPlainLatinIsNotArabic() {
    assertThat(QuranUtils.doesStringContainArabic("hello")).isFalse()
  }

  @Test
  fun testArabicLetterIsDetected() {
    // \u0628 is BEH, a common Arabic letter
    assertThat(QuranUtils.doesStringContainArabic("\u0628")).isTrue()
  }

  @Test
  fun testBareHamzaIsDetected() {
    // \u0621 ARABIC LETTER HAMZA (1569) is the first Arabic letter and must be
    // detected as Arabic. It was previously excluded by an off-by-one lower bound.
    assertThat(QuranUtils.doesStringContainArabic("\u0621")).isTrue()
  }

  @Test
  fun testHamzaLedWordIsDetected() {
    // A query that begins with a bare hamza before any other Arabic letter.
    assertThat(QuranUtils.doesStringContainArabic("\u0621\u0646\u062a\u0645")).isTrue()
  }

  @Test
  fun testAlefMaddaIsDetected() {
    // \u0622 ALEF WITH MADDA ABOVE (1570), the previous lower bound
    assertThat(QuranUtils.doesStringContainArabic("\u0622")).isTrue()
  }

  @Test
  fun testYehIsDetected() {
    // \u064A YEH (1610), the upper bound
    assertThat(QuranUtils.doesStringContainArabic("\u064A")).isTrue()
  }
}
