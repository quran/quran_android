package com.quran.data.page.provider.impl

import android.view.Display

internal class WarshPageProvider(display: Display) : DefaultPageProvider(display) {
  private val baseUrl = "http://android.quran.com/data/warsh"

  override fun getImageVersion() = 2

  override fun getImageUrl() = "$baseUrl/"

  override fun getImageZipUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "warsh/audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "warsh/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "warsh"

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for warsh pages
  }
}
