package com.quran.labs.androidquran.util

import com.quran.data.dao.Settings
import javax.inject.Inject

class SettingsImpl @Inject constructor(private val quranSettings: QuranSettings) : Settings {
  override suspend fun setVersion(version: Int) {
    quranSettings.version = version
  }

  override suspend fun removeDidDownloadPages() {
    quranSettings.removeDidDownloadPages()
  }

  override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) {
    quranSettings.setShouldOverlayPageInfo(shouldOverlay)
  }
}
