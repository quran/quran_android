package com.quran.data.page.provider

import com.google.common.truth.Truth.assertThat
import com.quran.data.page.provider.edition.Madani1421PageProvider
import com.quran.data.page.provider.edition.Madani1439LinesPageProvider
import com.quran.data.page.provider.edition.Madani1440PageProvider
import com.quran.data.page.provider.edition.Madani1441LinesPageProvider
import com.quran.data.page.provider.edition.ShemerlyPageProvider
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.page.provider.tajweed.TajweedPageProvider
import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import org.junit.Test

/** The page types of quran.com's Page Type (experimental) list, as its own app downloads them. */
class PageEditionsTest {

  private class Edition(
    val key: String,
    val provider: PageProvider,
    val folder: String,
    val width: String,
    val version: Int,
    val directory: String,
    val mushafType: MushafType,
    val pages: Int,
  )

  private val editions = listOf(
    Edition(TajweedPageProvider.KEY, TajweedPageProvider(), "tajweed", "1280", 7, "tajweed", MushafType.MADANI_1405, 604),
    Edition(Madani1421PageProvider.KEY, Madani1421PageProvider(), "madani_1421", "1120", 2, "new_madani", MushafType.MADANI_1440, 604),
    Edition(Madani1440PageProvider.KEY, Madani1440PageProvider(), "madani_1440", "1352", 4, "new_madani", MushafType.MADANI_1440, 604),
    Edition(Madani1439LinesPageProvider.KEY, Madani1439LinesPageProvider(), "madani_1439", "1080", 1, "new_madani", MushafType.MADANI_1440, 604),
    Edition(Madani1441LinesPageProvider.KEY, Madani1441LinesPageProvider(), "madani_1441", "1440", 1, "new_madani", MushafType.MADANI_1440, 604),
    Edition(ShemerlyPageProvider.KEY, ShemerlyPageProvider(), "shemerly", "1200", 1, "shemerly", MushafType.SHEMERLY, 521),
  )

  @Test
  fun `each edition downloads from its folder on files quran app`() {
    editions.forEach { edition ->
      val provider = edition.provider
      val base = "https://files.quran.app/hafs/${edition.folder}"
      assertThat(provider.getImagesZipBaseUrl()).isEqualTo("$base/zips/")
      assertThat(provider.getAyahInfoBaseUrl()).isEqualTo("$base/databases/ayahinfo/")
      assertThat(provider.getPatchBaseUrl()).isEqualTo("$base/patches/v")
      assertThat(provider.getImageVersion()).isEqualTo(edition.version)
    }
  }

  @Test
  fun `each edition has a single width`() {
    editions.forEach { edition ->
      listOf(DisplaySize(720, 1600), DisplaySize(1440, 3200), DisplaySize(2560, 1600)).forEach { size ->
        val calculator = edition.provider.getPageSizeCalculator(size)
        calculator.setOverrideParameter("1920")
        assertThat(calculator.getWidthParameter()).isEqualTo(edition.width)
        assertThat(calculator.getTabletWidthParameter()).isEqualTo(edition.width)
      }
    }
  }

  @Test
  fun `each edition keeps its files apart from the Madani ones`() {
    editions.forEach { edition ->
      assertThat(edition.provider.getImagesDirectoryName()).isEqualTo(edition.directory)
      // where the images zip puts its own databases folder
      assertThat(edition.provider.getAyahInfoDirectoryName()).isEqualTo("${edition.directory}/databases")
    }
    // editions sharing a directory differ by width
    editions.groupBy { it.directory }.values.forEach { shared ->
      assertThat(shared.map { it.width }.toSet()).hasSize(shared.size)
    }
  }

  @Test
  fun `each edition has its pages and the Madani reciters`() {
    val madaniQaris = MadaniPageProvider().getQaris().map { it.id }
    editions.forEach { edition ->
      assertThat(edition.provider.mushafType()).isEqualTo(edition.mushafType)
      assertThat(edition.provider.getDataSource().numberOfPages).isEqualTo(edition.pages)
      // the memorize feature's hint voices among them
      assertThat(edition.provider.getQaris().map { it.id }).isEqualTo(madaniQaris)
    }
  }

  @Test
  fun `only the editions named lines are drawn line by line`() {
    val byKey = editions.associateBy { it.key }
    editions.forEach { edition ->
      val content = edition.provider.getPageContentType()
      // the checks for missing page images skip these types by their name
      assertThat(content is PageContentType.Line).isEqualTo(edition.key.endsWith("lines"))
      if (content is PageContentType.Line) {
        val fallback = byKey.getValue(edition.provider.getFallbackPageType()!!)
        assertThat(fallback.provider.getPageContentType()).isEqualTo(PageContentType.Image)
        assertThat(fallback.mushafType).isEqualTo(edition.mushafType)
      }
    }
  }
}
