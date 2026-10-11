package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.ReadingBookmarkType.BLUE
import com.quran.data.model.bookmark.ReadingBookmarkType.GREEN
import com.quran.data.model.bookmark.ReadingBookmarkType.PURPLE
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import kotlin.time.Instant

class ReadingBookmarkSelectorTest {
  private val quranInfo = QuranInfo(MadaniDataSource())
  private val selector = ReadingBookmarkSelector(quranInfo)
  private val destination = SuraAyah(2, 255)
  private val destinationPage = quranInfo.getPageFromSuraAyah(2, 255)

  @Test
  fun `explicit placed color wins over exact ayah and recency`() {
    val bookmarks = listOf(page(GREEN, 1), ayah(BLUE, destination, 10))
    assertThat(selector.select(destination, bookmarks, explicitType = GREEN)).isEqualTo(GREEN)
  }

  @Test
  fun `unplaced explicit color is ignored and latest exact ayah wins`() {
    val bookmarks = listOf(
      ayah(GREEN, destination, 2),
      ayah(BLUE, destination, 3),
      EmptyReadingBookmark(PURPLE, time(20))
    )
    assertThat(selector.select(destination, bookmarks, explicitType = PURPLE)).isEqualTo(BLUE)
  }

  @Test
  fun `exact ayah beats a newer bookmark elsewhere on its page`() {
    val bookmarks = listOf(ayah(GREEN, destination), page(BLUE, destinationPage, 10))
    assertThat(selector.select(destination, bookmarks)).isEqualTo(GREEN)
  }

  @Test
  fun `same page includes ayah bookmarks and uses latest timestamp`() {
    val bookmarks = listOf(page(GREEN, destinationPage), ayah(BLUE, destination, 10))
    assertThat(selector.select(Page(destinationPage), bookmarks)).isEqualTo(BLUE)
    assertThat(selector.select(SuraAyah(2, 256), bookmarks)).isEqualTo(BLUE)
  }

  @Test
  fun `same page beats a nearby predecessor and previous preference`() {
    val bookmarks = listOf(page(GREEN, 100), page(BLUE, 95, 10))
    assertThat(selector.select(Page(100), bookmarks, previousType = BLUE)).isEqualTo(GREEN)
  }

  @Test
  fun `closest predecessor wins with a three page advantage even when older`() {
    val bookmarks = listOf(page(GREEN, 90), page(BLUE, 87, 10), page(PURPLE, 101, 20))
    assertThat(selector.select(Page(100), bookmarks, previousType = BLUE)).isEqualTo(GREEN)
  }

  @Test
  fun `equal and two page gaps are ambiguous but three is sufficient`() {
    for (gap in 0..3) {
      val bookmarks = listOf(page(GREEN, 90), page(BLUE, 90 - gap, 10))
      val expected = if (gap == 3) GREEN else BLUE
      assertThat(selector.select(Page(100), bookmarks)).isEqualTo(expected)
    }
  }

  @Test
  fun `twenty five page window includes boundary but not page twenty six`() {
    val bookmarks = listOf(page(GREEN, 75), page(BLUE, 200, 10))
    assertThat(selector.select(Page(100), bookmarks)).isEqualTo(GREEN)
    assertThat(selector.select(Page(101), bookmarks)).isEqualTo(BLUE)
  }

  @Test
  fun `runner up outside the window still prevents an ambiguous selection`() {
    val bookmarks = listOf(page(GREEN, 75), page(BLUE, 74, 10))
    assertThat(selector.select(Page(100), bookmarks)).isEqualTo(BLUE)
  }

  @Test
  fun `ayah bookmarks also participate in proximity selection`() {
    val bookmarks = listOf(ayah(GREEN, destination), page(BLUE, destinationPage - 3, 10))
    assertThat(selector.select(Page(destinationPage + 25), bookmarks)).isEqualTo(GREEN)
  }

  @Test
  fun `previous placed preference beats overall recency when proximity is ambiguous`() {
    val bookmarks = listOf(page(GREEN, 90), page(BLUE, 89, 10))
    assertThat(selector.select(Page(100), bookmarks, previousType = GREEN)).isEqualTo(GREEN)
  }

  @Test
  fun `cleared preference and bookmarks ahead fall back to latest placed`() {
    val bookmarks = listOf(page(GREEN, 200), page(BLUE, 201, 10), EmptyReadingBookmark(PURPLE, time(20)))
    assertThat(selector.select(Page(100), bookmarks, previousType = PURPLE)).isEqualTo(BLUE)
    assertThat(selector.select(null, bookmarks)).isEqualTo(BLUE)
  }

  @Test
  fun `no placed bookmarks falls back to purple`() {
    val bookmarks = listOf(EmptyReadingBookmark(GREEN, time(10)))
    assertThat(selector.select(destination, bookmarks, GREEN, GREEN)).isEqualTo(PURPLE)
    assertThat(selector.select(null, emptyList())).isEqualTo(PURPLE)
  }

  private fun page(slot: ReadingBookmarkType, page: Int, timestamp: Long = 1) =
    PageReadingBookmark(slot, page, time(timestamp))

  private fun ayah(slot: ReadingBookmarkType, ayah: SuraAyah, timestamp: Long = 1) =
    AyahReadingBookmark(slot, ayah.sura, ayah.ayah, time(timestamp))

  private fun time(seconds: Long) = Instant.fromEpochSeconds(seconds)
}
