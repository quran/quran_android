package com.quran.data.page.provider.shemerly

import com.quran.data.page.provider.common.QuranDataSourceProvider
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator

internal class ShemerlyPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data"
  private val shemerlyBaseUrl = "$baseUrl/shemerly"

  override fun getDataSource() = QuranDataSourceProvider.provideShemerlyDataSource()

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      ShemerlyPageSizeCalculator()

  override fun getImageVersion() = 1

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
}
