package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for [QuranInfo].
 *
 * QuranInfo is the central class for all Quran metadata operations including:
 * - Page/Sura/Ayah lookups and conversions
 * - Juz (part) calculations
 * - Position/navigation calculations for ViewPager
 * - Validation of page/sura/ayah bounds
 *
 * Test coverage includes:
 * - Normal operations
 * - Boundary conditions (first/last pages, suras, ayahs)
 * - Invalid inputs (negative, zero, beyond bounds)
 * - Edge cases (page transitions, sura boundaries)
 */
class QuranInfoTest {

  private lateinit var quranInfo: QuranInfo
  private lateinit var dataSource: MadaniDataSource

  @Before
  fun setup() {
    dataSource = MadaniDataSource()
    quranInfo = QuranInfo(dataSource)
  }

  // ==================== Juz Tests ====================

  @Test
  fun testCorrectJuzBounds() {
    val data = dataSource.pageForJuzArray
    data.forEachIndexed { index, value ->
      assertThat(quranInfo.getJuzFromPage(value)).isEqualTo(index + 1)
    }
  }

  @Test
  fun testCorrectJuzWithinJuz() {
    val data = dataSource.pageForJuzArray
    data.forEachIndexed { index, value ->
      // juz' x page plus 10 pages should still be juz' x
      assertThat(quranInfo.getJuzFromPage(value + 10)).isEqualTo(index + 1)
      // the page before this juz' should be the previous juz'
      assertThat(quranInfo.getJuzFromPage(value - 1)).isEqualTo(index)
    }
  }

  @Test
  fun testFirstJuz() {
    val firstPageOfSecondJuz = dataSource.pageForJuzArray[1]
    for (i in 1 until firstPageOfSecondJuz) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(1)
    }
  }

  @Test
  fun testThirtiethJuz() {
    val firstPageOfLastJuz = dataSource.pageForJuzArray[29]
    val lastPageOfMushaf = dataSource.numberOfPages
    for (i in firstPageOfLastJuz until lastPageOfMushaf) {
      assertThat(quranInfo.getJuzFromPage(i)).isEqualTo(30)
    }
  }

  @Test
  fun testDisplayJuz() {
    // make sure that juz' 7 starts on page 121, but the display juz' is 6
    assertThat(quranInfo.getJuzForDisplayFromPage(121)).isEqualTo(6)
    assertThat(quranInfo.getJuzFromPage(121)).isEqualTo(7)

    // make sure that juz' 11 starts on page 201, but the display juz' is 10
    assertThat(quranInfo.getJuzForDisplayFromPage(201)).isEqualTo(10)
    assertThat(quranInfo.getJuzFromPage(201)).isEqualTo(11)
  }

  @Test
  fun testGetStartingPageForJuz() {
    // Juz 1 starts on page 1
    assertThat(quranInfo.getStartingPageForJuz(1)).isEqualTo(1)

    // Verify all 30 juz starting pages
    val juzPages = dataSource.pageForJuzArray
    for (juz in 1..30) {
      assertThat(quranInfo.getStartingPageForJuz(juz)).isEqualTo(juzPages[juz - 1])
    }
  }

  @Test
  fun testGetJuzFromSuraAyah() {
    // Al-Fatiha 1:1 is in Juz 1
    assertThat(quranInfo.getJuzFromSuraAyah(1, 1, 1)).isEqualTo(1)

    // Last ayah of Quran is in Juz 30
    assertThat(quranInfo.getJuzFromSuraAyah(114, 6, 30)).isEqualTo(30)

    // Boundary: If already at juz 30, should return 30
    assertThat(quranInfo.getJuzFromSuraAyah(114, 1, 30)).isEqualTo(30)
  }

  // ==================== Page Validation Tests ====================

  @Test
  fun testIsValidPage_validPages() {
    // First page is valid
    assertThat(quranInfo.isValidPage(1)).isTrue()

    // Middle page is valid
    assertThat(quranInfo.isValidPage(300)).isTrue()

    // Last page is valid
    assertThat(quranInfo.isValidPage(dataSource.numberOfPages)).isTrue()
  }

  @Test
  fun testIsValidPage_invalidPages() {
    // Page 0 is invalid
    assertThat(quranInfo.isValidPage(0)).isFalse()

    // Negative pages are invalid
    assertThat(quranInfo.isValidPage(-1)).isFalse()

    // Beyond last page is invalid
    assertThat(quranInfo.isValidPage(dataSource.numberOfPages + 1)).isFalse()

    // Large page numbers are invalid
    assertThat(quranInfo.isValidPage(1000)).isFalse()
  }

  // ==================== Sura Tests ====================

  @Test
  fun testGetPageNumberForSura() {
    // Al-Fatiha starts on page 1
    assertThat(quranInfo.getPageNumberForSura(1)).isEqualTo(1)

    // Al-Baqarah starts on page 2
    assertThat(quranInfo.getPageNumberForSura(2)).isEqualTo(2)

    // Verify all sura start pages match data source
    val suraPages = dataSource.pageForSuraArray
    for (sura in 1..114) {
      assertThat(quranInfo.getPageNumberForSura(sura)).isEqualTo(suraPages[sura - 1])
    }
  }

  @Test
  fun testGetSuraNumberFromPage() {
    // Page 1 has Al-Fatiha starting on it (sura 1)
    assertThat(quranInfo.getSuraNumberFromPage(1)).isEqualTo(1)

    // Page 2 has Al-Baqarah starting on it (sura 2)
    assertThat(quranInfo.getSuraNumberFromPage(2)).isEqualTo(2)

    // getSuraNumberFromPage returns the sura that starts on that page,
    // or the sura before if no sura starts on that page
    // For the last page, verify it returns a valid sura (may not be 114)
    val lastPageSura = quranInfo.getSuraNumberFromPage(dataSource.numberOfPages)
    assertThat(lastPageSura).isIn(1..114)
  }

  @Test
  fun testGetSuraOnPage() {
    // First ayah on page 1 is from sura 1
    assertThat(quranInfo.getSuraOnPage(1)).isEqualTo(1)

    // First ayah on page 2 is from sura 2
    assertThat(quranInfo.getSuraOnPage(2)).isEqualTo(2)
  }

  @Test
  fun testGetListOfSurahWithStartingOnPage() {
    // Page 1 only has Al-Fatiha starting
    val page1Suras = quranInfo.getListOfSurahWithStartingOnPage(1)
    assertThat(page1Suras).containsExactly(1)

    // Page 2 has Al-Baqarah starting
    val page2Suras = quranInfo.getListOfSurahWithStartingOnPage(2)
    assertThat(page2Suras).containsExactly(2)

    // Last page (604) has multiple short suras
    // An-Nas (114) starts on page 604
    val lastPageSuras = quranInfo.getListOfSurahWithStartingOnPage(604)
    assertThat(lastPageSuras).contains(114)
  }

  @Test
  fun testGetNumberOfAyahs_validSuras() {
    // Al-Fatiha has 7 ayahs
    assertThat(quranInfo.getNumberOfAyahs(1)).isEqualTo(7)

    // Al-Baqarah has 286 ayahs (longest sura)
    assertThat(quranInfo.getNumberOfAyahs(2)).isEqualTo(286)

    // Al-Kawthar (sura 108) has 3 ayahs (shortest sura)
    assertThat(quranInfo.getNumberOfAyahs(108)).isEqualTo(3)

    // An-Nas has 6 ayahs
    assertThat(quranInfo.getNumberOfAyahs(114)).isEqualTo(6)
  }

  @Test
  fun testGetNumberOfAyahs_invalidSuras() {
    // Sura 0 is invalid
    assertThat(quranInfo.getNumberOfAyahs(0)).isEqualTo(-1)

    // Negative sura is invalid
    assertThat(quranInfo.getNumberOfAyahs(-1)).isEqualTo(-1)

    // Sura 115 is invalid
    assertThat(quranInfo.getNumberOfAyahs(115)).isEqualTo(-1)
  }

  @Test
  fun testGetNumberOfAyahsInQuran() {
    // Total ayahs in Quran is 6236
    assertThat(quranInfo.getNumberOfAyahsInQuran()).isEqualTo(6236)
  }

  @Test
  fun testIsMakki() {
    // Al-Fatiha is Makki
    assertThat(quranInfo.isMakki(1)).isTrue()

    // Al-Baqarah is Madani (not Makki)
    assertThat(quranInfo.isMakki(2)).isFalse()

    // Al-Ikhlas (112) is Makki
    assertThat(quranInfo.isMakki(112)).isTrue()
  }

  // ==================== Ayah Tests ====================

  @Test
  fun testGetFirstAyahOnPage() {
    // First ayah on page 1 is ayah 1
    assertThat(quranInfo.getFirstAyahOnPage(1)).isEqualTo(1)

    // First ayah on page 2 is ayah 1 (start of Al-Baqarah)
    assertThat(quranInfo.getFirstAyahOnPage(2)).isEqualTo(1)
  }

  @Test
  fun testGetAyahId() {
    // First ayah (1:1) has ID 1
    assertThat(quranInfo.getAyahId(1, 1)).isEqualTo(1)

    // Last ayah of Al-Fatiha (1:7) has ID 7
    assertThat(quranInfo.getAyahId(1, 7)).isEqualTo(7)

    // First ayah of Al-Baqarah (2:1) has ID 8
    assertThat(quranInfo.getAyahId(2, 1)).isEqualTo(8)
  }

  @Test
  fun testGetSuraAyahFromAyahId() {
    // Ayah ID 1 is 1:1
    assertThat(quranInfo.getSuraAyahFromAyahId(1)).isEqualTo(SuraAyah(1, 1))

    // Ayah ID 7 is 1:7 (last of Al-Fatiha)
    assertThat(quranInfo.getSuraAyahFromAyahId(7)).isEqualTo(SuraAyah(1, 7))

    // Ayah ID 8 is 2:1 (first of Al-Baqarah)
    assertThat(quranInfo.getSuraAyahFromAyahId(8)).isEqualTo(SuraAyah(2, 1))
  }

  @Test
  fun testAyahIdRoundTrip() {
    // Test that getAyahId and getSuraAyahFromAyahId are inverse operations
    val testCases = listOf(
      SuraAyah(1, 1),
      SuraAyah(1, 7),
      SuraAyah(2, 1),
      SuraAyah(2, 286),
      SuraAyah(114, 1),
      SuraAyah(114, 6)
    )

    for (suraAyah in testCases) {
      val ayahId = quranInfo.getAyahId(suraAyah.sura, suraAyah.ayah)
      val result = quranInfo.getSuraAyahFromAyahId(ayahId)
      assertThat(result).isEqualTo(suraAyah)
    }
  }

  @Test
  fun testDiff() {
    val start = SuraAyah(1, 1)
    val end = SuraAyah(1, 7)

    // Diff from 1:1 to 1:7 is 6 ayahs
    assertThat(quranInfo.diff(start, end)).isEqualTo(6)

    // Diff in reverse is negative
    assertThat(quranInfo.diff(end, start)).isEqualTo(-6)

    // Diff of same ayah is 0
    assertThat(quranInfo.diff(start, start)).isEqualTo(0)
  }

  @Test
  fun testDiff_acrossSuras() {
    val start = SuraAyah(1, 7)
    val end = SuraAyah(2, 1)

    // From last ayah of Fatiha to first of Baqarah is 1
    assertThat(quranInfo.diff(start, end)).isEqualTo(1)
  }

  // ==================== Page Lookup Tests ====================

  @Test
  fun testGetPageFromSuraAyah_validInputs() {
    // 1:1 is on page 1
    assertThat(quranInfo.getPageFromSuraAyah(1, 1)).isEqualTo(1)

    // 2:1 is on page 2
    assertThat(quranInfo.getPageFromSuraAyah(2, 1)).isEqualTo(2)
  }

  @Test
  fun testGetPageFromSuraAyah_invalidInputs() {
    // Invalid sura 0
    assertThat(quranInfo.getPageFromSuraAyah(0, 1)).isEqualTo(-1)

    // Invalid sura 115
    assertThat(quranInfo.getPageFromSuraAyah(115, 1)).isEqualTo(-1)

    // Invalid negative sura
    assertThat(quranInfo.getPageFromSuraAyah(-1, 1)).isEqualTo(-1)

    // Invalid ayah beyond max
    assertThat(quranInfo.getPageFromSuraAyah(1, 287)).isEqualTo(-1)
  }

  @Test
  fun testGetPageFromSuraAyah_ayahZeroTreatedAsOne() {
    // Ayah 0 should be treated as ayah 1
    val pageForAyah0 = quranInfo.getPageFromSuraAyah(1, 0)
    val pageForAyah1 = quranInfo.getPageFromSuraAyah(1, 1)
    assertThat(pageForAyah0).isEqualTo(pageForAyah1)
  }

  @Test
  fun testGetPageBounds_firstPage() {
    val bounds = quranInfo.getPageBounds(1)

    // bounds[0] = start sura, bounds[1] = start ayah
    // bounds[2] = end sura, bounds[3] = end ayah
    assertThat(bounds[0]).isEqualTo(1) // Start sura is Al-Fatiha
    assertThat(bounds[1]).isEqualTo(1) // Start ayah is 1
  }

  @Test
  fun testGetPageBounds_lastPage() {
    val lastPage = dataSource.numberOfPages
    val bounds = quranInfo.getPageBounds(lastPage)

    // Last page ends with sura 114, ayah 6
    assertThat(bounds[2]).isEqualTo(114)
    assertThat(bounds[3]).isEqualTo(6)
  }

  @Test
  fun testGetPageBounds_clampsInvalidPages() {
    // Page beyond max is clamped to max
    val beyondMax = quranInfo.getPageBounds(dataSource.numberOfPages + 10)
    val lastPageBounds = quranInfo.getPageBounds(dataSource.numberOfPages)
    assertThat(beyondMax).isEqualTo(lastPageBounds)

    // Page below min is clamped to min
    val belowMin = quranInfo.getPageBounds(0)
    val firstPageBounds = quranInfo.getPageBounds(1)
    assertThat(belowMin).isEqualTo(firstPageBounds)
  }

  @Test
  fun testGetVerseRangeForPage() {
    val range = quranInfo.getVerseRangeForPage(1)

    assertThat(range.startSura).isEqualTo(1)
    assertThat(range.startAyah).isEqualTo(1)
    assertThat(range).isInstanceOf(VerseRange::class.java)
    assertThat(range.versesInRange).isGreaterThan(0)
  }

  // ==================== Rub'/Quarter Tests ====================

  @Test
  fun testGetRub3FromPage_validPages() {
    // getRub3FromPage returns which quarter (rub3) starts on a page
    // Returns -1 if no quarter starts on that page
    // Returns the quarter number (1-indexed) if a quarter starts on the page

    // Page 1 has no quarter starting (returns -1 for MadaniDataSource)
    val rub3Page1 = quranInfo.getRub3FromPage(1)
    // Value can be -1 (no quarter starts) or positive (quarter number)
    assertThat(rub3Page1).isIn(-1..240) // 240 = 30 juz * 8 quarters

    // Page 5 has quarter 1 starting (based on MadaniDataSource)
    val rub3Page5 = quranInfo.getRub3FromPage(5)
    assertThat(rub3Page5).isEqualTo(1)
  }

  @Test
  fun testGetRub3FromPage_invalidPages() {
    // Page 0 returns -1
    assertThat(quranInfo.getRub3FromPage(0)).isEqualTo(-1)

    // Page beyond max returns -1
    assertThat(quranInfo.getRub3FromPage(dataSource.numberOfPages + 1)).isEqualTo(-1)
  }

  @Test
  fun testGetQuarterByIndex() {
    val quarters = dataSource.quartersArray

    // Verify we can access all quarters
    for (i in quarters.indices) {
      val quarter = quranInfo.getQuarterByIndex(i)
      assertThat(quarter).isEqualTo(quarters[i])
    }
  }

  // ==================== Manzil Tests ====================

  @Test
  fun testManzilForPage() {
    // manzilForPage returns the manzil index for the page
    // The return value depends on the manzil data in the data source
    // If manzil array is empty or page is before all manzils, returns -1
    // Otherwise returns the index of the manzil the page falls into
    val manzilArray = dataSource.manzilPageArray

    if (manzilArray.isNotEmpty()) {
      // If we have manzil data, test that the last page returns valid manzil count
      val lastPageManzil = quranInfo.manzilForPage(dataSource.numberOfPages)
      assertThat(lastPageManzil).isEqualTo(manzilArray.size)
    } else {
      // If no manzil data, all pages return -1
      val manzil = quranInfo.manzilForPage(1)
      assertThat(manzil).isEqualTo(-1)
    }
  }

  // ==================== Dual Page Mapping Tests ====================

  @Test
  fun testMapSinglePageToDualPage() {
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

  @Test
  fun testDualPageMappingRoundTrip() {
    // For even pages, mapSinglePageToDualPage and mapDualPageToSinglePage should be consistent
    for (page in 2..10 step 2) {
      val dualPage = quranInfo.mapSinglePageToDualPage(page)
      val singlePage = quranInfo.mapDualPageToSinglePage(dualPage)
      // The single page should be the same or page - 1 (the "first" of the dual)
      assertThat(singlePage).isIn(listOf(page, page - 1))
    }
  }

  // ==================== Position/Navigation Tests ====================

  @Test
  fun testGetPageFromPosition_singlePage() {
    val numberOfPages = quranInfo.numberOfPagesConsideringSkipped

    // Position 0 should be the last page
    assertThat(quranInfo.getPageFromPosition(0, isDualPagesVisible = false))
      .isEqualTo(numberOfPages)

    // Last position should be page 1
    assertThat(quranInfo.getPageFromPosition(numberOfPages - 1, isDualPagesVisible = false))
      .isEqualTo(1)
  }

  @Test
  fun testGetPositionFromPage_singlePage() {
    val numberOfPages = quranInfo.numberOfPagesConsideringSkipped

    // Page 1 should be at last position
    assertThat(quranInfo.getPositionFromPage(1, isDualPagesVisible = false))
      .isEqualTo(numberOfPages - 1)

    // Last page should be at position 0
    assertThat(quranInfo.getPositionFromPage(numberOfPages, isDualPagesVisible = false))
      .isEqualTo(0)
  }

  @Test
  fun testPositionPageRoundTrip_singlePage() {
    // Test that getPageFromPosition and getPositionFromPage are inverse operations
    for (page in listOf(1, 50, 100, 300, 604)) {
      val position = quranInfo.getPositionFromPage(page, isDualPagesVisible = false)
      val resultPage = quranInfo.getPageFromPosition(position, isDualPagesVisible = false)
      assertThat(resultPage).isEqualTo(page)
    }
  }

  @Test
  fun testGetPageFromPosition_dualPages() {
    // In dual page mode, position 0 returns the last dual page
    val page = quranInfo.getPageFromPosition(0, isDualPagesVisible = true)
    assertThat(page).isGreaterThan(0)
  }

  @Test
  fun testGetPositionFromPage_dualPages() {
    val position1 = quranInfo.getPositionFromPage(1, isDualPagesVisible = true)
    val position2 = quranInfo.getPositionFromPage(2, isDualPagesVisible = true)

    // Pages 1 and 2 should map to the same position in dual mode
    // or position2 should be <= position1 since we read right to left
    assertThat(position1).isAtLeast(position2)
  }

  // ==================== Data Integrity Tests ====================

  @Test
  fun testSuraPageStartMatchesDataSource() {
    val dataSourcePages = dataSource.pageForSuraArray
    assertThat(quranInfo.suraPageStart).isEqualTo(dataSourcePages)
  }

  @Test
  fun testQuartersMatchDataSource() {
    val dataSourceQuarters = dataSource.quartersArray
    assertThat(quranInfo.quarters).isEqualTo(dataSourceQuarters)
  }

  @Test
  fun testNumberOfPagesMatchesDataSource() {
    assertThat(quranInfo.numberOfPages).isEqualTo(dataSource.numberOfPages)
  }

  @Test
  fun testSkipMatchesDataSource() {
    assertThat(quranInfo.skip).isEqualTo(dataSource.pagesToSkip)
  }

  // ==================== Test Helper Class ====================

  private class SkippingDataSource(skipCount: Int = 1) : MadaniDataSource() {
    override val pagesToSkip: Int = skipCount
  }
}
