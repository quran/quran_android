package com.quran.data.source

import androidx.annotation.StringRes

interface PageProvider {
  fun getDataSource(): QuranDataSource
  fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator

  fun getImageVersion(): Int

  fun getImagesBaseUrl(): String
  fun getImagesZipBaseUrl(): String
  fun getPatchBaseUrl(): String
  fun getAyahInfoBaseUrl(): String
  fun getDatabasesBaseUrl(): String
  fun getAudioDatabasesBaseUrl(): String

  fun getAudioDirectoryName(): String
  fun getDatabaseDirectoryName(): String
  fun getAyahInfoDirectoryName(): String
  fun getImagesDirectoryName(): String

  @StringRes fun getPreviewTitle(): Int
  @StringRes fun getPreviewDescription(): Int
}
