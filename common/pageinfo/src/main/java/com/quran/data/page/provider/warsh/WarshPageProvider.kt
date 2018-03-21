package com.quran.data.page.provider.warsh

import com.quran.data.page.provider.common.QuranDataSourceProvider
import com.quran.data.page.provider.common.size.NoOverridePageSizeCalculator
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator

internal class WarshPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data"
  private val warshBaseUrl = "$baseUrl/warsh"

  override fun getDataSource() = QuranDataSourceProvider.provideWarshDataSource()

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NoOverridePageSizeCalculator(displaySize)

  override fun getImageVersion() = 2

  override fun getImagesBaseUrl() = "$warshBaseUrl/"

  override fun getImagesZipBaseUrl() = "$warshBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$warshBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$warshBaseUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  "$warshBaseUrl/audio/"

  override fun getAudioDirectoryName() = "warsh/audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "warsh/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "warsh"
}
