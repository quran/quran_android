package com.quran.data.page.provider.shemerly

import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.qaris

internal class ShemerlyPageProvider : PageProvider {
  companion object {
    private const val baseUrl = "https://files.quran.app/hafs"
    private const val shemerlyBaseUrl = "$baseUrl/shemerly"
    private val dataSource by lazy { ShemerlyDataSource() }
  }

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      ShemerlyPageSizeCalculator()

  override fun getImageVersion() = 1

  // pages 1..521
  override fun mushafType() = MushafType.SHEMERLY

  override fun getImagesBaseUrl() = "$shemerlyBaseUrl/"

  override fun getImagesZipBaseUrl() = "$shemerlyBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$shemerlyBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$shemerlyBaseUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  getDatabasesBaseUrl() + "/audio/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "shemerly/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "shemerly"

  override fun getPreviewTitle() = R.string.shemerly_title

  override fun getPreviewDescription() = R.string.shemerly_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris() = qaris()
}
