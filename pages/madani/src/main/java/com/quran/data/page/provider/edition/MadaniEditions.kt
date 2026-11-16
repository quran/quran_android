package com.quran.data.page.provider.edition

import com.quran.data.source.MushafType
import com.quran.data.source.PageContentType
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.pages.data.madani.Madani1440DataSource
import com.quran.labs.androidquran.pages.data.shemerly.ShemerlyDataSource
import com.quran.labs.androidquran.pages.madani.R

// The editions of quran.com's Page Type (experimental) list, under the keys its own app uses.

/** The King Fahd Complex's newer prints share one page layout, and one directory. */
private const val NEW_MADANI = "new_madani"
private val madani1440Pages by lazy { Madani1440DataSource() }
private val shemerlyPages by lazy { ShemerlyDataSource() }

/** Madani Mushaf (1421). */
class Madani1421PageProvider : HafsEditionPageProvider(
  folder = "madani_1421",
  width = "1120",
  imageVersion = 2,
  directory = NEW_MADANI,
  title = R.string.madani_1421_title,
  description = R.string.madani_1421_description,
) {
  override fun getDataSource(): QuranDataSource = madani1440Pages

  override fun mushafType() = MushafType.MADANI_1440

  companion object {
    const val KEY = "new_madani"
  }
}

/** Madani Mushaf (1440). */
class Madani1440PageProvider : HafsEditionPageProvider(
  folder = "madani_1440",
  width = "1352",
  imageVersion = 4,
  directory = NEW_MADANI,
  title = R.string.madani_1440_title,
  description = R.string.madani_1440_description,
) {
  override fun getDataSource(): QuranDataSource = madani1440Pages

  override fun mushafType() = MushafType.MADANI_1440

  companion object {
    const val KEY = "new_madani_1441"
  }
}

/**
 * The editions drawn line by line (feature:linebyline): each page is 15 line images. Without them
 * the app falls back to the 1440 edition, as quran.com's app does.
 */
abstract class MadaniLinesPageProvider(
  folder: String,
  width: String,
  private val pageType: String,
  title: Int,
  description: Int,
) : HafsEditionPageProvider(folder, width, imageVersion = 1, NEW_MADANI, title, description) {

  override fun getDataSource(): QuranDataSource = madani1440Pages

  override fun mushafType() = MushafType.MADANI_1440

  override fun getPageContentType() =
    PageContentType.Line(ratio = 174f / 1080f, lineHeight = 174, allowOverlapOfLines = true)

  override fun getFallbackPageType() = Madani1440PageProvider.KEY

  // the line view picks its marker colors by it
  override fun pageType() = pageType
}

/** Madani Mushaf (1439), optimized for larger screens. */
class Madani1439LinesPageProvider : MadaniLinesPageProvider(
  folder = "madani_1439",
  width = "1080",
  pageType = "madani_1439",
  title = R.string.madani_1439_lines_title,
  description = R.string.madani_1439_lines_description,
) {
  companion object {
    // ends in "lines": the checks for missing page images skip it
    const val KEY = "new_madani_1439_lines"
  }
}

/** Madani Mushaf (1441), optimized for larger screens. */
class Madani1441LinesPageProvider : MadaniLinesPageProvider(
  folder = "madani_1441",
  width = "1440",
  pageType = "madani_1441",
  title = R.string.madani_1441_lines_title,
  description = R.string.madani_1441_lines_description,
) {
  companion object {
    const val KEY = "madani_1441_lines"
  }
}

/** The Shemerly print, popular in Egypt: 521 pages. */
class ShemerlyPageProvider : HafsEditionPageProvider(
  folder = "shemerly",
  width = "1200",
  imageVersion = 1,
  directory = "shemerly",
  title = R.string.shemerly_title,
  description = R.string.shemerly_description,
) {
  override fun getDataSource(): QuranDataSource = shemerlyPages

  override fun mushafType() = MushafType.SHEMERLY

  companion object {
    const val KEY = "shemerly"
  }
}
