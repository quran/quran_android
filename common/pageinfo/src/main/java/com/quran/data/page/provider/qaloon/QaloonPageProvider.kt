package com.quran.data.page.provider.qaloon

import com.quran.data.page.provider.common.size.NoOverridePageSizeCalculator
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator

internal class QaloonPageProvider : PageProvider {
  private val baseUrl = "http://android.quran.com/data"
  private val qaloonBaseUrl = "$baseUrl/qaloon"
  private val dataSource = QaloonDataSource()

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NoOverridePageSizeCalculator(displaySize)

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
}
