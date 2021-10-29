package com.quran.data.dao

import kotlinx.coroutines.flow.Flow

interface Settings {
  suspend fun setVersion(version: Int)
  suspend fun removeDidDownloadPages()
  suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean)
  suspend fun lastPage(): Int
  suspend fun isNightMode(): Boolean
  suspend fun nightModeTextBrightness(): Int
  suspend fun shouldShowHeaderFooter(): Boolean
  suspend fun shouldShowBookmarks(): Boolean
  fun preferencesFlow(): Flow<String>
}
