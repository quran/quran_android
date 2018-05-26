package com.quran.labs.androidquran.data

import com.google.common.truth.Truth.assertThat
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.pageinfo.common.MadaniDataSource
import org.junit.Test

class QuranInfoTest {

  @Test
  fun testCorrectJuzBounds() {
    val quranInfo = QuranInfo(MadaniPageProvider())
    val data = MadaniDataSource().getPageForJuzArray()
    data.forEachIndexed { index, value ->
      assertThat(quranInfo.getJuzFromPage(value)).isEqualTo(index + 1)
    }
  }

  @Test
  fun testCorrectJuzWithinJuz() {
    val quranInfo = QuranInfo(MadaniPageProvider())
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
    val quranInfo = QuranInfo(MadaniPageProvider())
    val dataSource = MadaniDataSource()
    val firstPageOfSecondJuz = dataSource.getPageForJuzArray()[1]
    for (i in 1 until firstPageOfSecondJuz) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(1)
    }
  }

  @Test
  fun testThirtiethJuz() {
    val quranInfo = QuranInfo(MadaniPageProvider())
    val dataSource = MadaniDataSource()
    val firstPageOfLastJuz = dataSource.getPageForJuzArray()[29]
    val lastPageOfMushaf = dataSource.getNumberOfPages()
    for (i in firstPageOfLastJuz until lastPageOfMushaf) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(30)
    }
  }

}
