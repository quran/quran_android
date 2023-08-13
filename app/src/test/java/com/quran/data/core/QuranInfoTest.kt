package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.pageinfo.common.MadaniDataSource
import org.junit.Test

class QuranInfoTest {

  @Test
  fun testCorrectJuzBounds() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val data = MadaniDataSource().pageForJuzArray
    data.forEachIndexed { index, value ->
      assertThat(quranInfo.getJuzFromPage(value)).isEqualTo(index + 1)
    }
  }

  @Test
  fun testCorrectJuzWithinJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val data = MadaniDataSource().pageForJuzArray
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
    val firstPageOfSecondJuz = dataSource.pageForJuzArray[1]
    for (i in 1 until firstPageOfSecondJuz) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(1)
    }
  }

  @Test
  fun testThirtiethJuz() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val dataSource = MadaniDataSource()
    val firstPageOfLastJuz = dataSource.pageForJuzArray[29]
    val lastPageOfMushaf = dataSource.numberOfPages
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

  @Test
  fun testMapSinglePageToDualPage() {
    val quranInfo = QuranInfo(MadaniDataSource())
    assertThat(quranInfo.mapSinglePageToDualPage(1)).isEqualTo(2)
    assertThat(quranInfo.mapSinglePageToDualPage(2)).isEqualTo(2)
    assertThat(quranInfo.mapSinglePageToDualPage(3)).isEqualTo(4)
    assertThat(quranInfo.mapSinglePageToDualPage(4)).isEqualTo(4)

    // skipping a single page (ex naskh), so the first page is 2
    val quranInfoThatSkips = QuranInfo(SkippingDataSource())
    assertThat(quranInfoThatSkips.mapSinglePageToDualPage(2)).isEqualTo(3)
    assertThat(quranInfoThatSkips.mapSinglePageToDualPage(3)).isEqualTo(3)
    assertThat(quranInfoThatSkips.mapSinglePageToDualPage(4)).isEqualTo(5)
    assertThat(quranInfoThatSkips.mapSinglePageToDualPage(5)).isEqualTo(5)

    // hypothetical example where we skip 2 pages, so the first page is 3
    val quranInfoThatSkipsExtra = QuranInfo(SkippingDataSource(2))
    assertThat(quranInfoThatSkipsExtra.mapSinglePageToDualPage(3)).isEqualTo(4)
    assertThat(quranInfoThatSkipsExtra.mapSinglePageToDualPage(4)).isEqualTo(4)
    assertThat(quranInfoThatSkipsExtra.mapSinglePageToDualPage(5)).isEqualTo(6)
    assertThat(quranInfoThatSkipsExtra.mapSinglePageToDualPage(6)).isEqualTo(6)
  }

  @Test
  fun testMapDualPageToSinglePage() {
    val quranInfo = QuranInfo(MadaniDataSource())
    assertThat(quranInfo.mapDualPageToSinglePage(1)).isEqualTo(1)
    assertThat(quranInfo.mapDualPageToSinglePage(2)).isEqualTo(1)
    assertThat(quranInfo.mapDualPageToSinglePage(3)).isEqualTo(3)
    assertThat(quranInfo.mapDualPageToSinglePage(4)).isEqualTo(3)

    // skipping a single page (ex naskh), so the first page is 2
    val quranInfoThatSkips = QuranInfo(SkippingDataSource())
    assertThat(quranInfoThatSkips.mapDualPageToSinglePage(2)).isEqualTo(2)
    assertThat(quranInfoThatSkips.mapDualPageToSinglePage(3)).isEqualTo(2)
    assertThat(quranInfoThatSkips.mapDualPageToSinglePage(4)).isEqualTo(4)
    assertThat(quranInfoThatSkips.mapDualPageToSinglePage(5)).isEqualTo(4)

    // hypothetical example where we skip 2 pages, so the first page is 3
    val quranInfoThatSkipsExtra = QuranInfo(SkippingDataSource(2))
    assertThat(quranInfoThatSkipsExtra.mapDualPageToSinglePage(3)).isEqualTo(3)
    assertThat(quranInfoThatSkipsExtra.mapDualPageToSinglePage(4)).isEqualTo(3)
    assertThat(quranInfoThatSkipsExtra.mapDualPageToSinglePage(5)).isEqualTo(5)
    assertThat(quranInfoThatSkipsExtra.mapDualPageToSinglePage(6)).isEqualTo(5)
  }

  private class SkippingDataSource(skipCount: Int = 1) : MadaniDataSource() {
    override val pagesToSkip: Int = skipCount
  }
}
