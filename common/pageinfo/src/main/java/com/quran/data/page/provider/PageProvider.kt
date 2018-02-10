package com.quran.data.page.provider

interface PageProvider {
  fun getWidthParameter(): String
  fun getTabletWidthParameter(): String
  fun setOverrideParameter(parameter: String)

  fun getImageVersion(): Int

  fun getImageUrl(): String
  fun getImageZipUrl(): String
  fun getPatchBaseUrl(): String
  fun getAyahInfoUrl(): String

  fun getAudioDirectoryName(): String
  fun getDatabaseDirectoryName(): String
  fun getAyahInfoDirectoryName(): String
  fun getImagesDirectoryName(): String
}
