package com.quran.data.source

import com.quran.data.source.QuranDataSource

interface PageProvider {
  fun getDataSource(): QuranDataSource

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
}
