package com.quran.data.core

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.ReadingBookmarkType.RED
import com.quran.data.model.bookmark.ReadingBookmarkType.TEAL
import com.quran.data.model.bookmark.ReadingBookmarkType.ORANGE
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
    val bookmarks = listOf(page(TEAL, 1), ayah(RED, destination, 10))
    assertThat(selector.select(destination, bookmarks, explicitType = TEAL)).isEqualTo(TEAL)
  }

  @Test
  fun `unplaced explicit color is ignored and latest exact ayah wins`() {
    val bookmarks = listOf(
      ayah(TEAL, destination, 2),
      ayah(RED, destination, 3),
      EmptyReadingBookmark(ORANGE, time(20))
    )
    assertThat(selector.select(destination, bookmarks, explicitType = ORANGE)).isEqualTo(RED)
  }

  @Test
  fun `exact ayah beats a newer bookmark elsewhere on its page`() {
    val bookmarks = listOf(ayah(TEAL, destination), page(RED, destinationPage, 10))
    assertThat(selector.select(destination, bookmarks)).isEqualTo(TEAL)
  }

  @Test
  fun `same page includes ayah bookmarks and uses latest timestamp`() {
    val bookmarks = listOf(page(TEAL, destinationPage), ayah(RED, destination, 10))
    assertThat(selector.select(Page(destinationPage), bookmarks)).isEqualTo(RED)
    assertThat(selector.select(SuraAyah(2, 256), bookmarks)).isEqualTo(RED)
  }

  @Test
  fun `same page beats a nearby predecessor and previous preference`() {
    val bookmarks = listOf(page(TEAL, 100), page(RED, 95, 10))
    assertThat(selector.select(Page(100), bookmarks, previousType = RED)).isEqualTo(TEAL)
  }

  @Test
  fun `closest predecessor wins with a three page advantage even when older`() {
    val bookmarks = listOf(page(TEAL, 90), page(RED, 87, 10), page(ORANGE, 101, 20))
    assertThat(selector.select(Page(100), bookmarks, previousType = RED)).isEqualTo(TEAL)
  }

  @Test
  fun `equal and two page gaps are ambiguous but three is sufficient`() {
    for (gap in 0..3) {
      val bookmarks = listOf(page(TEAL, 90), page(RED, 90 - gap, 10))
      val expected = if (gap == 3) TEAL else RED
      assertThat(selector.select(Page(100), bookmarks)).isEqualTo(expected)
    }
  }

  @Test
  fun `twenty five page window includes boundary but not page twenty six`() {
    val bookmarks = listOf(page(TEAL, 75), page(RED, 200, 10))
    assertThat(selector.select(Page(100), bookmarks)).isEqualTo(TEAL)
    assertThat(selector.select(Page(101), bookmarks)).isEqualTo(RED)
  }

  @Test
  fun `runner up outside the window still prevents an ambiguous selection`() {
    val bookmarks = listOf(page(TEAL, 75), page(RED, 74, 10))
    assertThat(selector.select(Page(100), bookmarks)).isEqualTo(RED)
  }

  @Test
  fun `ayah bookmarks also participate in proximity selection`() {
    val bookmarks = listOf(ayah(TEAL, destination), page(RED, destinationPage - 3, 10))
    assertThat(selector.select(Page(destinationPage + 25), bookmarks)).isEqualTo(TEAL)
  }

  @Test
  fun `previous placed preference beats overall recency when proximity is ambiguous`() {
    val bookmarks = listOf(page(TEAL, 90), page(RED, 89, 10))
    assertThat(selector.select(Page(100), bookmarks, previousType = TEAL)).isEqualTo(TEAL)
  }

  @Test
  fun `cleared preference and bookmarks ahead fall back to latest placed`() {
    val bookmarks = listOf(page(TEAL, 200), page(RED, 201, 10), EmptyReadingBookmark(ORANGE, time(20)))
    assertThat(selector.select(Page(100), bookmarks, previousType = ORANGE)).isEqualTo(RED)
    assertThat(selector.select(null, bookmarks)).isEqualTo(RED)
  }

  @Test
  fun `no placed bookmarks falls back to orange`() {
    val bookmarks = listOf(EmptyReadingBookmark(TEAL, time(10)))
    assertThat(selector.select(destination, bookmarks, TEAL, TEAL)).isEqualTo(ORANGE)
    assertThat(selector.select(null, emptyList())).isEqualTo(ORANGE)
  }

  private fun page(slot: ReadingBookmarkType, page: Int, timestamp: Long = 1) =
    PageReadingBookmark(slot, page, time(timestamp))

  private fun ayah(slot: ReadingBookmarkType, ayah: SuraAyah, timestamp: Long = 1) =
    AyahReadingBookmark(slot, ayah.sura, ayah.ayah, time(timestamp))

  private fun time(seconds: Long) = Instant.fromEpochSeconds(seconds)
}
