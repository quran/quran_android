package com.quran.data.page.provider.tajweed

import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.qaris

class TajweedPageProvider : PageProvider {

  companion object {
    private const val baseUrl = "https://files.quran.app/hafs"
    private const val tajweedBaseUrl = "$baseUrl/tajweed"
    private val dataSource by lazy { TajweedDataSource() }
  }

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
    TajweedPageSizeCalculator()

  override fun getImageVersion() = 7

  override fun mushafType() = MushafType.MADANI_1405

  override fun getImagesBaseUrl() = "$tajweedBaseUrl/"

  override fun getImagesZipBaseUrl() = "$tajweedBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$tajweedBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$tajweedBaseUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  getDatabasesBaseUrl() + "/audio/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "tajweed/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "tajweed"

  override fun getPreviewTitle() = R.string.tajweed_title

  override fun getPreviewDescription() = R.string.tajweed_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris() = qaris()
}
