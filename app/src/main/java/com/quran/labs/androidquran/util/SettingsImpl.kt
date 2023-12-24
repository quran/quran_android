package com.quran.labs.androidquran.util

import android.content.SharedPreferences
import com.quran.data.dao.Settings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsImpl @Inject constructor(private val quranSettings: QuranSettings) : Settings {
  private val scope = MainScope()

  private val preferencesFlow =
    callbackFlow {
      val prefsCallback =
        SharedPreferences.OnSharedPreferenceChangeListener { _, pref ->
          if (pref != null) {
            trySendBlocking(pref)
              .onFailure {}
          }
        }
      quranSettings.registerPreferencesListener(prefsCallback)
      awaitClose { quranSettings.unregisterPreferencesListener(prefsCallback) }
    }

    // removing WhileSubscribed here breaks release versions of the app most likely
    // due to being garbage collection with no strong references to the job (note
    // that the callbacks are WeakReferences within SharedPreferences). see
    // this issue for https://github.com/Kotlin/kotlinx.coroutines/issues/2557
    // details. While the aforementioned issue is fixed in coroutines, this issue
    // still happens unless we either use WhileSubscribed as here, or we keep a
    // strong reference to the SharedPreferenceChangeListener. See also this issue
    // https://github.com/Kotlin/kotlinx.coroutines/issues/1061.
    .shareIn(scope, SharingStarted.WhileSubscribed())

  override suspend fun setVersion(version: Int) {
    quranSettings.version = version
  }

  override suspend fun removeDidDownloadPages() {
    quranSettings.removeDidDownloadPages()
  }

  override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) {
    quranSettings.setShouldOverlayPageInfo(shouldOverlay)
  }

  override suspend fun lastPage(): Int {
    return quranSettings.lastPage
  }

  override suspend fun isNightMode(): Boolean {
    return quranSettings.isNightMode
  }

  override suspend fun nightModeTextBrightness(): Int {
    return quranSettings.nightModeTextBrightness
  }

  override suspend fun nightModeBackgroundBrightness(): Int {
    return quranSettings.nightModeBackgroundBrightness
  }

  override suspend fun shouldShowHeaderFooter(): Boolean {
    return quranSettings.shouldOverlayPageInfo()
  }

  override suspend fun shouldShowBookmarks(): Boolean {
    return quranSettings.shouldHighlightBookmarks()
  }

  override suspend fun pageType(): String {
    return quranSettings.pageType
  }

  override suspend fun showSidelines(): Boolean {
    return quranSettings.isSidelines
  }

  override suspend fun setShowSidelines(show: Boolean) {
    quranSettings.isSidelines = show
  }

  override suspend fun showLineDividers(): Boolean {
    return quranSettings.isShowLineDividers
  }

  override suspend fun setShouldShowLineDividers(show: Boolean) {
    quranSettings.isShowLineDividers = show
  }

  override fun preferencesFlow(): Flow<String> = preferencesFlow
}
