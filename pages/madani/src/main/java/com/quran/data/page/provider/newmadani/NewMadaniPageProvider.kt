package com.quran.data.page.provider.newmadani

import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.qaris

internal open class NewMadaniPageProvider : PageProvider {
  companion object {
    const val baseUrl = "https://files.quran.app/hafs"
    private val dataSource by lazy { NewMadaniDataSource() }
  }

  protected open val newMadaniBaseUrl = "$baseUrl/madani_1421"

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NewMadaniPageSizeCalculator()

  override fun getImageVersion() = 2
  override fun ayahUnderlineRaiseRatio() = 0.15f

  // the newer 1441 and line-by-line page types that extend this provider use the same edition
  override fun mushafType() = MushafType.MADANI_1440

  override fun getImagesBaseUrl() = "$newMadaniBaseUrl/"

  override fun getImagesZipBaseUrl() = "$newMadaniBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$newMadaniBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$newMadaniBaseUrl/databases/ayahinfo/"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  getDatabasesBaseUrl() + "/audio/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "new_madani/" + getDatabaseDirectoryName()

  override fun getImagesDirectoryName() = "new_madani"

  override fun getPreviewTitle() = R.string.new_madani_title

  override fun getPreviewDescription() = R.string.new_madani_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris() = qaris()
}
