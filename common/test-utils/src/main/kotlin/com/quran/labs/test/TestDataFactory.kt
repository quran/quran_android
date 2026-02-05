package com.quran.labs.test

/**
 * Factory for creating test data objects.
 *
 * This factory provides methods to create consistent test fixtures
 * for common data types used throughout the Quran app.
 *
 * Usage:
 * ```
 * val sura = TestDataFactory.createSura(number = 1)
 * val ayah = TestDataFactory.createAyah(sura = 1, ayah = 1)
 * val page = TestDataFactory.createPage(page = 1, sura = 1, ayah = 1)
 * ```
 */
object TestDataFactory {

  /**
   * Quran constants for testing.
   */
  object QuranConstants {
    const val TOTAL_SURAS = 114
    const val TOTAL_AYAHS = 6236
    const val TOTAL_PAGES_MADANI = 604
    const val FIRST_JUZ_PAGE = 1
    const val BISMILLAH_SURA_INDEX = 0
    const val FATIHA_SURA = 1
    const val BAQARAH_SURA = 2
    const val LAST_SURA = 114
  }

  /**
   * Test data for a Sura (chapter).
   */
  data class TestSura(
    val number: Int,
    val name: String,
    val englishName: String,
    val ayahCount: Int,
    val startPage: Int,
    val isMakki: Boolean
  )

  /**
   * Test data for an Ayah (verse).
   */
  data class TestAyah(
    val sura: Int,
    val ayah: Int,
    val page: Int,
    val text: String = "Test ayah text for sura $sura, ayah $ayah"
  )

  /**
   * Test data for a Page.
   */
  data class TestPage(
    val page: Int,
    val sura: Int,
    val ayah: Int,
    val juz: Int = calculateJuz(page)
  )

  /**
   * Test data for a Bookmark.
   */
  data class TestBookmark(
    val id: Long,
    val sura: Int?,
    val ayah: Int?,
    val page: Int,
    val timestamp: Long = System.currentTimeMillis()
  )

  /**
   * Creates a test Sura with default values.
   */
  fun createSura(
    number: Int = 1,
    name: String = "Test Sura $number",
    englishName: String = "Test Chapter $number",
    ayahCount: Int = 7,
    startPage: Int = 1,
    isMakki: Boolean = true
  ): TestSura = TestSura(
    number = number,
    name = name,
    englishName = englishName,
    ayahCount = ayahCount,
    startPage = startPage,
    isMakki = isMakki
  )

  /**
   * Creates Al-Fatiha with accurate data.
   */
  fun createFatiha(): TestSura = TestSura(
    number = 1,
    name = "الفاتحة",
    englishName = "Al-Fatiha",
    ayahCount = 7,
    startPage = 1,
    isMakki = true
  )

  /**
   * Creates Al-Baqarah with accurate data.
   */
  fun createBaqarah(): TestSura = TestSura(
    number = 2,
    name = "البقرة",
    englishName = "Al-Baqarah",
    ayahCount = 286,
    startPage = 2,
    isMakki = false
  )

  /**
   * Creates a test Ayah with default values.
   */
  fun createAyah(
    sura: Int = 1,
    ayah: Int = 1,
    page: Int = 1,
    text: String = "Test ayah text for sura $sura, ayah $ayah"
  ): TestAyah = TestAyah(
    sura = sura,
    ayah = ayah,
    page = page,
    text = text
  )

  /**
   * Creates a list of test Ayahs for a range.
   */
  fun createAyahRange(
    sura: Int,
    startAyah: Int,
    endAyah: Int,
    page: Int = 1
  ): List<TestAyah> = (startAyah..endAyah).map { ayahNum ->
    createAyah(sura = sura, ayah = ayahNum, page = page)
  }

  /**
   * Creates a test Page with default values.
   */
  fun createPage(
    page: Int = 1,
    sura: Int = 1,
    ayah: Int = 1,
    juz: Int = calculateJuz(page)
  ): TestPage = TestPage(
    page = page,
    sura = sura,
    ayah = ayah,
    juz = juz
  )

  /**
   * Creates a list of test Pages.
   */
  fun createPages(startPage: Int, endPage: Int): List<TestPage> =
    (startPage..endPage).map { pageNum ->
      createPage(page = pageNum)
    }

  /**
   * Creates a test Bookmark with default values.
   */
  fun createBookmark(
    id: Long = 1L,
    sura: Int? = 1,
    ayah: Int? = 1,
    page: Int = 1,
    timestamp: Long = System.currentTimeMillis()
  ): TestBookmark = TestBookmark(
    id = id,
    sura = sura,
    ayah = ayah,
    page = page,
    timestamp = timestamp
  )

  /**
   * Creates a page-only bookmark (without specific ayah).
   */
  fun createPageBookmark(
    id: Long = 1L,
    page: Int = 1,
    timestamp: Long = System.currentTimeMillis()
  ): TestBookmark = TestBookmark(
    id = id,
    sura = null,
    ayah = null,
    page = page,
    timestamp = timestamp
  )

  /**
   * Creates multiple bookmarks for testing.
   */
  fun createBookmarks(count: Int): List<TestBookmark> =
    (1..count).map { index ->
      createBookmark(
        id = index.toLong(),
        sura = (index % QuranConstants.TOTAL_SURAS) + 1,
        ayah = index,
        page = (index % QuranConstants.TOTAL_PAGES_MADANI) + 1
      )
    }

  /**
   * Calculates the Juz number for a given page (approximate).
   * Note: This is a simplified calculation for testing purposes.
   */
  private fun calculateJuz(page: Int): Int {
    return when {
      page <= 0 -> 1
      page > QuranConstants.TOTAL_PAGES_MADANI -> 30
      else -> ((page - 1) / 20) + 1
    }.coerceIn(1, 30)
  }

  /**
   * Returns valid page numbers for boundary testing.
   */
  fun validPageBoundaries(): List<Int> = listOf(
    1,  // First page
    2,  // Second page (start of Baqarah)
    QuranConstants.TOTAL_PAGES_MADANI / 2,  // Middle page
    QuranConstants.TOTAL_PAGES_MADANI - 1,  // Second to last
    QuranConstants.TOTAL_PAGES_MADANI  // Last page
  )

  /**
   * Returns invalid page numbers for error testing.
   */
  fun invalidPageNumbers(): List<Int> = listOf(
    -1,  // Negative
    0,   // Zero
    QuranConstants.TOTAL_PAGES_MADANI + 1,  // Beyond last
    Int.MAX_VALUE  // Overflow
  )

  /**
   * Returns valid sura numbers for boundary testing.
   */
  fun validSuraBoundaries(): List<Int> = listOf(
    QuranConstants.FATIHA_SURA,  // First sura
    QuranConstants.BAQARAH_SURA,  // Second sura
    57,  // Middle sura
    QuranConstants.LAST_SURA - 1,  // Second to last
    QuranConstants.LAST_SURA  // Last sura
  )

  /**
   * Returns invalid sura numbers for error testing.
   */
  fun invalidSuraNumbers(): List<Int> = listOf(
    -1,  // Negative
    0,   // Zero (suras are 1-indexed)
    QuranConstants.LAST_SURA + 1,  // Beyond last
    Int.MAX_VALUE  // Overflow
  )
}
