package com.quran.data.page.provider.impl

import com.quran.data.page.provider.PageProvider

internal class ShemerlyPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data/shemerly"

  override fun getImageVersion() = 1

  override fun getImageUrl() = "$baseUrl/"

  override fun getImageZipUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "shemerly/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "shemerly"

  override fun getWidthParameter() = "1200"

  override fun getTabletWidthParameter(): String {
    // use the same size for tablet landscape
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    // override parameter is irrelevant for shemerly pages
  }
}
