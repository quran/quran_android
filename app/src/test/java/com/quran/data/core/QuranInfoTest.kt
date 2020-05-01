package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.pageinfo.common.MadaniDataSource
import org.junit.Test

class QuranInfoTest {

  @Test
  fun testCorrectJuzBounds() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val data = MadaniDataSource().getPageForJuzArray()
    data.forEachIndexed { index, value ->
      assertThat(quranInfo.getJuzFromPage(value)).isEqualTo(index + 1)
    }
  }

  @Test
  fun testCorrectJuzWithinJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val data = MadaniDataSource().getPageForJuzArray()
    data.forEachIndexed { index, value ->
      // juz' x page plus 10 pages should still be juz' x
      assertThat(quranInfo.getJuzFromPage(value + 10)).isEqualTo(index + 1)
      // the page before this juz' should be the previous juz'
      assertThat(quranInfo.getJuzFromPage(value - 1)).isEqualTo(index)
    }
  }

  @Test
  fun testFirstJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val dataSource = MadaniDataSource()
    val firstPageOfSecondJuz = dataSource.getPageForJuzArray()[1]
    for (i in 1 until firstPageOfSecondJuz) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(1)
    }
  }

  @Test
  fun testThirtiethJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val dataSource = MadaniDataSource()
    val firstPageOfLastJuz = dataSource.getPageForJuzArray()[29]
    val lastPageOfMushaf = dataSource.getNumberOfPages()
    for (i in firstPageOfLastJuz until lastPageOfMushaf) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(30)
    }
  }

  @Test
  fun testDisplayJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    // make sure that juz' 7 starts on page 121, but the display juz' is 6
    assertThat(quranInfo.getJuzForDisplayFromPage(121)).isEqualTo(6)
    assertThat(quranInfo.getJuzFromPage(121)).isEqualTo(7)

    // make sure that juz' 11 starts on page 201, but the display juz' is 10
    assertThat(quranInfo.getJuzForDisplayFromPage(201)).isEqualTo(10)
    assertThat(quranInfo.getJuzFromPage(201)).isEqualTo(11)
  }
}
