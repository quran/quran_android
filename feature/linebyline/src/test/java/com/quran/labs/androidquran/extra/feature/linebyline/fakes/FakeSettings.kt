package com.quran.labs.androidquran.extra.feature.linebyline.fakes

import com.quran.data.dao.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeSettings : Settings {
  private val data = mutableMapOf<String, Any>()
  val preferencesSharedFlow = MutableSharedFlow<String>()

  fun setNightMode(value: Boolean) { data["isNightMode"] = value }
  fun setTextColor(value: Int) { data["pageThemeTextColor"] = value }
  fun setBackgroundColor(value: Int) { data["backgroundColor"] = value }
  fun setHeaderFooter(value: Boolean) { data["shouldShowHeaderFooter"] = value }
  fun setSidelinesEnabled(value: Boolean) { data["showSidelines"] = value }
  fun setLineDividersEnabled(value: Boolean) { data["showLineDividers"] = value }

  suspend fun emitPreferencesChange(key: String = "") {
    preferencesSharedFlow.emit(key)
  }

  override suspend fun setVersion(version: Int) {}
  override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) {}
  override suspend fun lastPage(): Int = 1
  override suspend fun isNightMode(): Boolean = data["isNightMode"] as? Boolean ?: false
  override suspend fun pageThemeTextColor(): Int = data["pageThemeTextColor"] as? Int ?: -0x1000000
  override suspend fun pageThemeBackgroundColor(): Int = data["backgroundColor"] as? Int ?: -1
  override suspend fun shouldShowHeaderFooter(): Boolean = data["shouldShowHeaderFooter"] as? Boolean ?: false
  override suspend fun shouldShowBookmarks(): Boolean = false
  override suspend fun setPageType(pageType: String) {}
  override suspend fun pageType(): String = ""
  override suspend fun showSidelines(): Boolean = data["showSidelines"] as? Boolean ?: false
  override suspend fun setShowSidelines(show: Boolean) { data["showSidelines"] = show }
  override suspend fun showLineDividers(): Boolean = data["showLineDividers"] as? Boolean ?: false
  override suspend fun setShouldShowLineDividers(show: Boolean) { data["showLineDividers"] = show }
  override suspend fun setAyahTextSize(value: Int) {}
  override suspend fun translationTextSize(): Int = 14

  override fun preferencesFlow(): Flow<String> = preferencesSharedFlow
}
