package com.quran.data.dao

import com.quran.data.model.PageAppearance
import kotlinx.coroutines.flow.Flow

interface Settings {
  suspend fun setVersion(version: Int)
  suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean)
  suspend fun lastPage(): Int
  suspend fun pageAppearance(): PageAppearance
  suspend fun pageTextBrightness(): Int
  suspend fun nightModeBackgroundBrightness(): Int
  // null for Light's shaded background
  suspend fun dayPageBackgroundColor(): Int?
  suspend fun shouldShowHeaderFooter(): Boolean
  suspend fun shouldShowBookmarks(): Boolean
  suspend fun pageType(): String
  suspend fun setPageType(pageType: String)
  suspend fun showSidelines(): Boolean
  suspend fun setShowSidelines(show: Boolean)
  suspend fun showLineDividers(): Boolean
  suspend fun setShouldShowLineDividers(show: Boolean)
  suspend fun setAyahTextSize(value: Int)
  suspend fun translationTextSize(): Int

  fun preferencesFlow(): Flow<String>
}
