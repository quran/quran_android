package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class QuranUtilsTest {

  @Test
  fun isUsingWesternDigitsWithLatnNumberingSystem() {
    assertThat(QuranUtils.isUsingWesternDigits(Locale.forLanguageTag("ar-EG-u-nu-latn"))).isTrue()
  }

  @Test
  fun isUsingWesternDigitsWithoutNumberingSystem() {
    assertThat(QuranUtils.isUsingWesternDigits(Locale.forLanguageTag("ar-EG"))).isFalse()
    assertThat(QuranUtils.isUsingWesternDigits(Locale.forLanguageTag("ar-EG-u-nu-arab"))).isFalse()
    assertThat(QuranUtils.isUsingWesternDigits(Locale.ENGLISH)).isFalse()
  }
}
