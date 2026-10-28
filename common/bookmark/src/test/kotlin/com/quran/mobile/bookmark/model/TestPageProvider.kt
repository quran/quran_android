package com.quran.mobile.bookmark.model

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource

/**
 * A page type whose pages mobile-sync numbers like [mushafType]. Only a page type whose layout a
 * test reads needs a [dataSource].
 */
internal class TestPageProvider(
  private val mushafType: MushafType,
  private val dataSource: QuranDataSource? = null
) : PageProvider {
  override fun getDataSource(): QuranDataSource =
    dataSource ?: throw UnsupportedOperationException("mobile-sync maps this page type's pages")

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator {
    throw UnsupportedOperationException()
  }

  override fun getImageVersion(): Int = 0

  override fun mushafType(): MushafType = mushafType

  override fun getImagesBaseUrl(): String = ""

  override fun getImagesZipBaseUrl(): String = ""

  override fun getPatchBaseUrl(): String = ""

  override fun getAyahInfoBaseUrl(): String = ""

  override fun getDatabasesBaseUrl(): String = ""

  override fun getAudioDatabasesBaseUrl(): String = ""

  override fun getAudioDirectoryName(): String = ""

  override fun getDatabaseDirectoryName(): String = ""

  override fun getAyahInfoDirectoryName(): String = ""

  override fun getImagesDirectoryName(): String = ""

  override fun getPreviewTitle(): Int = 0

  override fun getPreviewDescription(): Int = 0

  override fun getQaris(): List<Qari> = emptyList()

  override fun getDefaultQariId(): Int = 0
}
