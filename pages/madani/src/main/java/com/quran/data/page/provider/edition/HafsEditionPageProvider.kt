package com.quran.data.page.provider.edition

import androidx.annotation.StringRes
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator

/**
 * A Hafs mushaf edition as quran.com's own app downloads it from files.quran.app/hafs/[folder], at
 * a single width. Its reciters, and so the hints of the memorize feature, are the Madani page
 * type's; an edition with other pages also has its own data source and mushaf type.
 *
 * The images zip carries the edition's ayahinfo database too, so it lands in
 * [directory]/databases. Editions of the same layout family may share [directory]: their files
 * differ by width.
 */
abstract class HafsEditionPageProvider(
  folder: String,
  private val width: String,
  private val imageVersion: Int,
  private val directory: String,
  @StringRes private val title: Int,
  @StringRes private val description: Int,
  madani: MadaniPageProvider = MadaniPageProvider(),
) : PageProvider by madani {

  private val baseUrl = "$SERVER/$folder"

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
    FixedWidthCalculator(width)

  // the full zip holds a .vN file for each version up to this one
  override fun getImageVersion() = imageVersion

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getImagesDirectoryName() = directory

  override fun getAyahInfoDirectoryName() = "$directory/databases"

  override fun getPreviewTitle() = title

  override fun getPreviewDescription() = description

  private class FixedWidthCalculator(private val width: String) : PageSizeCalculator {
    override fun getWidthParameter() = width

    override fun getTabletWidthParameter() = width

    // there is only one width
    override fun setOverrideParameter(parameter: String) = Unit
  }

  private companion object {
    const val SERVER = "https://files.quran.app/hafs"
  }
}
