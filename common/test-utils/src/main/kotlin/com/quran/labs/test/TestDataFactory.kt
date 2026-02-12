package com.quran.labs.test

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag

/**
 * Factory for creating test data using REAL domain models.
 *
 * This factory provides methods to create consistent test fixtures
 * using the actual domain classes from the codebase, ensuring tests
 * validate real behavior.
 *
 * Usage:
 * ```
 * val bookmark = TestDataFactory.createBookmark(sura = 1, ayah = 1)
 * val suraAyah = TestDataFactory.createSuraAyah(sura = 2, ayah = 255)
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
    const val FATIHA_SURA = 1
    const val BAQARAH_SURA = 2
    const val LAST_SURA = 114
    const val FATIHA_AYAH_COUNT = 7
    const val BAQARAH_AYAH_COUNT = 286
  }

  // ==================== SuraAyah Factory ====================

  /**
   * Creates a [SuraAyah] with default values.
   */
  fun createSuraAyah(
    sura: Int = 1,
    ayah: Int = 1
  ): SuraAyah = SuraAyah(sura, ayah)

  /**
   * Creates SuraAyah for Al-Fatiha 1:1
   */
  fun fatihaStart(): SuraAyah = SuraAyah(1, 1)

  /**
   * Creates SuraAyah for the last ayah of Al-Fatiha (1:7)
   */
  fun fatihaEnd(): SuraAyah = SuraAyah(1, 7)

  /**
   * Creates SuraAyah for Al-Baqarah 2:255 (Ayat al-Kursi)
   */
  fun ayatAlKursi(): SuraAyah = SuraAyah(2, 255)

  /**
   * Creates SuraAyah for the last ayah of the Quran (114:6)
   */
  fun lastAyah(): SuraAyah = SuraAyah(114, 6)

  /**
   * Creates a list of SuraAyah for a range within a sura.
   */
  fun createSuraAyahRange(
    sura: Int,
    startAyah: Int,
    endAyah: Int
  ): List<SuraAyah> = (startAyah..endAyah).map { ayahNum ->
    SuraAyah(sura, ayahNum)
  }

  // ==================== Bookmark Factory ====================

  /**
   * Creates a [Bookmark] for a specific ayah with default values.
   */
  fun createBookmark(
    id: Long = 1L,
    sura: Int? = 1,
    ayah: Int? = 1,
    page: Int = 1,
    timestamp: Long = System.currentTimeMillis(),
    tags: List<Long> = emptyList()
  ): Bookmark = Bookmark(
    id = id,
    sura = sura,
    ayah = ayah,
    page = page,
    timestamp = timestamp,
    tags = tags
  )

  /**
   * Creates a page-only bookmark (without specific ayah).
   */
  fun createPageBookmark(
    id: Long = 1L,
    page: Int = 1,
    timestamp: Long = System.currentTimeMillis()
  ): Bookmark = Bookmark(
    id = id,
    sura = null,
    ayah = null,
    page = page,
    timestamp = timestamp
  )

  // ==================== Tag Factory ====================

  /**
   * Creates a [Tag] with default values.
   */
  fun createTag(
    id: Long = 1L,
    name: String = "Test Tag"
  ): Tag = Tag(id, name)

  /**
   * Creates multiple tags for testing.
   */
  fun createTags(count: Int): List<Tag> =
    (1..count).map { index ->
      Tag(index.toLong(), "Tag $index")
    }

  // ==================== RecentPage Factory ====================

  /**
   * Creates a [RecentPage] with default values.
   */
  fun createRecentPage(
    page: Int = 1,
    timestamp: Long = System.currentTimeMillis()
  ): RecentPage = RecentPage(page, timestamp)

  /**
   * Creates multiple recent pages for testing.
   */
  fun createRecentPages(count: Int): List<RecentPage> =
    (1..count).map { index ->
      RecentPage(
        page = (index % QuranConstants.TOTAL_PAGES_MADANI) + 1,
        timestamp = System.currentTimeMillis() - (index * 1000L)
      )
    }

  // ==================== Boundary Test Helpers ====================

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
