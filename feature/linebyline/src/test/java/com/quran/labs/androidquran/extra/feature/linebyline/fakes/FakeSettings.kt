package com.quran.labs.androidquran.extra.feature.linebyline.fakes

import com.quran.data.dao.Settings
import com.quran.data.model.PageAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeSettings : Settings {
  private val data = mutableMapOf<String, Any>()
  val preferencesSharedFlow = MutableSharedFlow<String>()

  fun setPageAppearance(value: PageAppearance) { data["pageAppearance"] = value }
  fun setTextBrightness(value: Int) { data["pageTextBrightness"] = value }
  fun setNightBackgroundBrightness(value: Int) { data["nightModeBackgroundBrightness"] = value }
  fun setDayBackgroundColor(value: Int) { data["dayPageBackgroundColor"] = value }
  fun setHeaderFooter(value: Boolean) { data["shouldShowHeaderFooter"] = value }
  fun setSidelinesEnabled(value: Boolean) { data["showSidelines"] = value }
  fun setLineDividersEnabled(value: Boolean) { data["showLineDividers"] = value }

  suspend fun emitPreferencesChange(key: String = "") {
    preferencesSharedFlow.emit(key)
  }

  override suspend fun setVersion(version: Int) {}
  override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) {}
  override suspend fun lastPage(): Int = 1
  override suspend fun pageAppearance(): PageAppearance =
    data["pageAppearance"] as? PageAppearance ?: PageAppearance.Light
  override suspend fun pageTextBrightness(): Int = data["pageTextBrightness"] as? Int ?: 255
  override suspend fun nightModeBackgroundBrightness(): Int = data["nightModeBackgroundBrightness"] as? Int ?: 0
  override suspend fun dayPageBackgroundColor(): Int? = data["dayPageBackgroundColor"] as? Int
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
