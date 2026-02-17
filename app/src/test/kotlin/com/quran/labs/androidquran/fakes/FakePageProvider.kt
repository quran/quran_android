package com.quran.labs.androidquran.fakes

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.PageProvider
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource

/**
 * Fake implementation of PageProvider for testing.
 *
 * Used by AudioUtilsTest to construct QariUtil and QuranFileUtils
 * without requiring a real page configuration. All methods return
 * minimal stubs — none are invoked by getLastAyahToPlay or
 * doesRequireBasmallah code paths.
 */
class FakePageProvider : PageProvider {

  override fun getDataSource(): QuranDataSource = MadaniDataSource()

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator {
    return object : PageSizeCalculator {
      override fun getWidthParameter(): String = ""
      override fun getTabletWidthParameter(): String = ""
      override fun setOverrideParameter(parameter: String) {}
    }
  }

  override fun getImageVersion(): Int = 0

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
