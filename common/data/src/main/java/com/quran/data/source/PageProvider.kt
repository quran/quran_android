package com.quran.data.source

import androidx.annotation.StringRes
import com.quran.data.model.audio.Qari

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

  fun ayahInfoDbHasGlyphData(): Boolean = false

  @StringRes fun getPreviewTitle(): Int
  @StringRes fun getPreviewDescription(): Int

  fun getPageContentType(): PageContentType = PageContentType.Image
  fun getFallbackPageType(): String? = null
  fun getQaris(): List<Qari>
  fun getDefaultQariId(): Int
  fun pageType(): String = ""
}
