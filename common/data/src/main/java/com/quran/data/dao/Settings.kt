package com.quran.data.dao

import kotlinx.coroutines.flow.Flow

interface Settings {
  suspend fun setVersion(version: Int)
  suspend fun removeDidDownloadPages()
  suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean)
  suspend fun lastPage(): Int
  suspend fun isNightMode(): Boolean
  suspend fun nightModeTextBrightness(): Int
  suspend fun nightModeBackgroundBrightness(): Int
  suspend fun shouldShowHeaderFooter(): Boolean
  suspend fun shouldShowBookmarks(): Boolean
  suspend fun pageType(): String
  suspend fun showSidelines(): Boolean
  suspend fun setShowSidelines(show: Boolean)
  suspend fun showLineDividers(): Boolean
  suspend fun setShouldShowLineDividers(show: Boolean)

  fun preferencesFlow(): Flow<String>
}
