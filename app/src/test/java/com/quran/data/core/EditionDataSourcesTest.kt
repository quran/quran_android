package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.pages.data.madani.Madani1440DataSource
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.shemerly.ShemerlyDataSource
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.Companion.pagesOfAyah
import org.junit.Test

/**
 * The page tables of the newer Madani prints and of the Shemerly print, made from their ayahinfo
 * databases: consistent with each other, and with a few pages checked against the layouts.
 */
class EditionDataSourcesTest {

  private val madani1440 = QuranInfo(Madani1440DataSource())
  private val shemerly = QuranInfo(ShemerlyDataSource())

  @Test
  fun `the tables are consistent`() {
    listOf(MadaniDataSource(), Madani1440DataSource(), ShemerlyDataSource()).forEach { source ->
      assertConsistent(source)
    }
  }

  @Test
  fun `the newer Madani prints begin 25 pages at other ayat`() {
    val classic = MadaniDataSource()
    val newer = Madani1440DataSource()
    val differing = (1..604).filter { page ->
      classic.suraForPageArray[page - 1] != newer.suraForPageArray[page - 1] ||
        classic.ayahForPageArray[page - 1] != newer.ayahForPageArray[page - 1]
    }
    assertThat(differing).hasSize(25)
    assertThat(newer.numberOfPages).isEqualTo(604)
    assertThat(newer.pageForSuraArray).isEqualTo(classic.pageForSuraArray)
    assertThat(newer.pageForJuzArray).isEqualTo(classic.pageForJuzArray)

    assertThat(firstAyah(madani1440, 121)).isEqualTo(SuraAyah(5, 78))
    assertThat(firstAyah(madani1440, 145)).isEqualTo(SuraAyah(6, 131))
    assertThat(firstAyah(madani1440, 600)).isEqualTo(SuraAyah(100, 6))
    assertThat(firstAyah(madani1440, 604)).isEqualTo(SuraAyah(112, 1))
  }

  @Test
  fun `the Shemerly print has 521 pages`() {
    assertThat(shemerly.numberOfPages).isEqualTo(521)
    assertThat(firstAyah(shemerly, 3)).isEqualTo(SuraAyah(2, 5))
    // pages beginning with the end of an ayah from the page before
    assertThat(firstAyah(shemerly, 305)).isEqualTo(SuraAyah(25, 73))
    assertThat(firstAyah(shemerly, 510)).isEqualTo(SuraAyah(89, 24))
    assertThat(firstAyah(shemerly, 438)).isEqualTo(SuraAyah(50, 34))
    assertThat(firstAyah(shemerly, 521)).isEqualTo(SuraAyah(112, 1))
    assertThat(shemerly.getPageFromSuraAyah(18, 1)).isEqualTo(242)
    assertThat(shemerly.getPageFromSuraAyah(114, 6)).isEqualTo(521)
    assertThat(shemerly.getStartingPageForJuz(25)).isEqualTo(403)
  }

  @Test
  fun `an ayah running on to the next page is on both`() {
    assertThat(pagesOfAyah(shemerly, 25, 73)).containsExactly(304, 305)
    assertThat(pagesOfAyah(shemerly, 25, 72)).containsExactly(304)
    assertThat(pagesOfAyah(shemerly, 25, 74)).containsExactly(305)
    assertThat(pagesOfAyah(shemerly, 114, 6)).containsExactly(521)
    assertThat(pagesOfAyah(shemerly, 1, 1)).containsExactly(1)
    // any ayah that begins a page might have begun on the page before
    val madani = QuranInfo(MadaniDataSource())
    assertThat(pagesOfAyah(madani, 2, 6)).containsExactly(2, 3)
    assertThat(pagesOfAyah(madani, 2, 7)).containsExactly(3)
  }

  private fun firstAyah(quranInfo: QuranInfo, page: Int) =
    SuraAyah(quranInfo.getSuraOnPage(page), quranInfo.getFirstAyahOnPage(page))

  private fun assertConsistent(source: QuranDataSource) {
    val pages = source.numberOfPages
    val quranInfo = QuranInfo(source)
    assertThat(source.suraForPageArray).hasLength(pages)
    assertThat(source.ayahForPageArray).hasLength(pages)
    assertThat(source.quarterStartByPage).hasLength(pages)
    assertThat(source.pageForSuraArray).hasLength(114)
    assertThat(source.pageForJuzArray).hasLength(30)

    // pages begin in order, at ayat that exist, each on its page or (running on) the page before
    var previous = SuraAyah(1, 1)
    for (page in 1..pages) {
      val first = firstAyah(quranInfo, page)
      assertThat(first).isAtLeast(previous)
      assertThat(first.ayah).isAtMost(source.numberOfAyahsForSuraArray[first.sura - 1])
      assertThat(quranInfo.getPageFromSuraAyah(first.sura, first.ayah)).isEqualTo(page)
      previous = first
    }
    // suras, quarters and juz begin on pages that have their first ayah
    for (sura in 1..114) {
      assertThat(source.pageForSuraArray[sura - 1]).isIn(pagesOfAyah(quranInfo, sura, 1))
    }
    for ((page, quarter) in source.quarterStartByPage.withIndex().filter { it.value >= 0 }) {
      val start = source.quartersArray[quarter]
      assertThat(page + 1).isIn(pagesOfAyah(quranInfo, start.sura, start.ayah))
    }
    for (juz in 0 until 30) {
      val start = source.quartersArray[juz * 8]
      assertThat(source.pageForJuzArray[juz]).isIn(pagesOfAyah(quranInfo, start.sura, start.ayah))
    }
    assertThat(quranInfo.getVerseRangeForPage(pages).endingSura).isEqualTo(114)
  }
}
