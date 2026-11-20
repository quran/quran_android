package com.quran.labs.androidquran.pages.common.madani

import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.common.audio.qaris
import com.quran.labs.androidquran.pages.common.madani.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource

class MadaniPageProvider : PageProvider {
  companion object {
    private const val baseUrl = "https://files.quran.app/hafs"
    private const val madaniUrl = "$baseUrl/madani"
    private val dataSource by lazy { MadaniDataSource() }
  }

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 8

  override fun mushafType() = MushafType.MADANI_1405

  override fun getImagesBaseUrl() = "$madaniUrl/"

  override fun getImagesZipBaseUrl() = "$madaniUrl/zips/"

  override fun getPatchBaseUrl() = "$madaniUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$madaniUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun ayahInfoDbHasGlyphData() = true

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = ""

  override fun getPreviewTitle() = R.string.madani_title

  override fun getPreviewDescription() = R.string.madani_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris() = qaris()
}
