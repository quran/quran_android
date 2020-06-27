package com.quran.data.model

import org.junit.Test

class SuraAyahTest {

  @Test
  fun testSuraAyahComparison() {
    val start = SuraAyah(7, 206)
    val ending = SuraAyah(8, 1)
    assert(ending > start)
    assert(SuraAyah(7, 206) == start)
  }

  @Test
  fun testSuraAyahComparisonSameSura() {
    val start = SuraAyah(7, 1)
    val ending = SuraAyah(7, 206)
    assert(ending > start)
  }
}
