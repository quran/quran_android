package com.quran.data.page.provider.naskh

import com.quran.data.page.provider.common.QuranDataSourceProvider
import com.quran.data.source.PageProvider

internal class NaskhPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data"
  private val baseNakshUrl = "$baseUrl/naskh"

  override fun getDataSource() = QuranDataSourceProvider.provideNaskhDataSource()

  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$baseNakshUrl/"

  override fun getImagesZipBaseUrl() = "$baseNakshUrl/zips/"

  override fun getPatchBaseUrl() = "$baseNakshUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseNakshUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "naskh/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "naskh"
}
