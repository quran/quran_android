package com.quran.data.page.provider.impl

internal class WarshPageProvider : DefaultPageProvider() {
  private val baseUrl = "http://android.quran.com/data"
  private val warshBaseUrl = "$baseUrl/warsh"

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
