package com.quran.data.page.provider.madani

import com.quran.data.page.provider.common.QuranDataSourceProvider
import com.quran.data.source.PageProvider

class MadaniPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data"

  override fun getDataSource() = QuranDataSourceProvider.provideMadaniDataSource()

  override fun getImageVersion() = 5

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = ""
}
