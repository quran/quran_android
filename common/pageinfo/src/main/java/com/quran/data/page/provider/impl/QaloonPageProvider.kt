package com.quran.data.page.provider.impl

import android.view.Display

internal class QaloonPageProvider(display: Display) : DefaultPageProvider(display) {
  private val baseUrl = "http://android.quran.com/data"
  private val qaloonBaseUrl = "$baseUrl/qaloon"

  override fun getImageVersion() = 2

  override fun getImagesBaseUrl() = "$qaloonBaseUrl/"

  override fun getImagesZipBaseUrl() = "$qaloonBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$qaloonBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$qaloonBaseUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  "$qaloonBaseUrl/audio/"

  override fun getAudioDirectoryName() = "qaloon/audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "qaloon/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "qaloon"

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for qaloon pages
  }
}
