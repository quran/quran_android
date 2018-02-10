package com.quran.data.page.provider.impl

import android.view.Display

internal class QaloonPageProvider(display: Display) : DefaultPageProvider(display) {
  private val baseUrl = "http://android.quran.com/data/qaloon"

  override fun getImageVersion() = 2

  override fun getImageUrl() = "$baseUrl/"

  override fun getImageZipUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "qaloon/audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "qaloon/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "qaloon"

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for qaloon pages
  }
}
