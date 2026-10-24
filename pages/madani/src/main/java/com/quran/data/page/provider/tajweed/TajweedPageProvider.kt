package com.quran.data.page.provider.tajweed

import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R

/**
 * The Madani mushaf with its tajweed rules in color, as quran.com's own app downloads it from
 * files.quran.app (one width, 1280 px). It has the same pages, ayat and reciters as the Madani page
 * type; only its images and glyph boxes differ. Shown under Page Type (experimental).
 */
class TajweedPageProvider(private val madani: MadaniPageProvider = MadaniPageProvider()) : PageProvider by madani {

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator = object : PageSizeCalculator {
    override fun getWidthParameter() = WIDTH
    override fun getTabletWidthParameter() = WIDTH
    override fun setOverrideParameter(parameter: String) = Unit
  }

  // a single version of these images: nothing to patch
  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$BASE_URL/"

  override fun getImagesZipBaseUrl() = "$BASE_URL/zips/"

  override fun getPatchBaseUrl() = "$BASE_URL/patches/v"

  override fun getAyahInfoBaseUrl() = "$BASE_URL/databases/ayahinfo/"

  // kept apart from the Madani pages, which have the same file names
  override fun getImagesDirectoryName() = "tajweed"

  override fun getAyahInfoDirectoryName() = "tajweed/databases"

  override fun getPreviewTitle() = R.string.tajweed_title

  override fun getPreviewDescription() = R.string.tajweed_description

  private companion object {
    const val BASE_URL = "https://files.quran.app/hafs/tajweed"
    const val WIDTH = "1280"
  }
}
