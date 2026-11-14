package com.quran.mobile.feature.memorize.tracker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhonemeNormalizerTest {

  @Test
  fun `madd lengths, gemination and ghunna collapse`() {
    // ٱلرَّحِيمِ as recited: doubled raa, long yaa madd
    assertThat(PhonemeNormalizer.normalize("ررَحِۦۦۦۦم")).isEqualTo("رَحِيم")
    // ghunna on a doubled meem
    assertThat(PhonemeNormalizer.normalize("ممممُبِۦۦۦۦن")).isEqualTo("مُبِين")
  }

  @Test
  fun `tajweed markers map to their letters or are dropped`() {
    assertThat(PhonemeNormalizer.normalize("ءَحَدڇ")).isEqualTo("ءَحَد") // qalqala
    assertThat(PhonemeNormalizer.normalize("مِںكُم")).isEqualTo("مِنكُم") // ikhfa
  }

  @Test
  fun `spaces are ignored`() {
    assertThat(PhonemeNormalizer.normalize("قُل هُوَ")).isEqualTo("قُلهُوَ")
  }

  @Test
  fun `lenient mode drops short vowels`() {
    assertThat(PhonemeNormalizer.normalize("بِسمِ للَااهِ", keepShortVowels = false)).isEqualTo("بسملاه")
  }
}
