package com.quran.labs.androidquran.presenter.data

import com.quran.analytics.AnalyticsProvider
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.presenter.data.QuranEventLogger.AudioPlaybackSource.PAGE
import com.quran.labs.androidquran.util.QuranSettings
import dagger.Reusable
import javax.inject.Inject

@Reusable
class QuranEventLogger @Inject constructor(
  private val analyticsProvider: AnalyticsProvider,
  private val quranSettings: QuranSettings
) {

  fun logAnalytics(isDualPages: Boolean, showingTranslations: Boolean, isSplitScreen: Boolean) {
    val isLockOrientation = quranSettings.isLockOrientation
    val lockingOrientation =  when {
      isLockOrientation && quranSettings.isLandscapeOrientation -> "landscape"
      isLockOrientation -> "portrait"
      else -> "no"
    }

    val params : Map<String, Any> = mapOf(
        "mode" to getScreenMode(isDualPages, showingTranslations, isSplitScreen),
        "pageType" to quranSettings.pageType,
        "isNightMode" to quranSettings.isNightMode,
        "isArabic" to quranSettings.isArabicNames,
        "background" to if (quranSettings.useNewBackground()) "default" else "legacy",
        "isLockingOrientation" to lockingOrientation,
        "overlayInfo" to quranSettings.shouldOverlayPageInfo(),
        "markerPopups" to quranSettings.shouldDisplayMarkerPopup(),
        "navigation" to if (quranSettings.navigateWithVolumeKeys()) "with_volume" else "default",
        "shouldHighlightBookmarks" to quranSettings.shouldHighlightBookmarks()
    )

    analyticsProvider.logEvent("quran_view", params)
  }

  fun logAudioPlayback(
    source: AudioPlaybackSource,
    qari: QariItem,
    isDualPages: Boolean,
    showingTranslations: Boolean,
    isSplitScreen: Boolean
  ) {
    val params : Map<String, Any> = mapOf(
        "id" to qari.id,
        "path" to qari.path,
        "isGapless" to qari.isGapless,
        "source" to if (source == PAGE) { "page" } else { "ayah" },
        "mode" to getScreenMode(isDualPages, showingTranslations, isSplitScreen)
    )
    analyticsProvider.logEvent("audio_playback", params)
  }

  fun switchToTranslationMode(translations: Int) {
    val params: Map<String, Any> = mutableMapOf("translations" to translations)
    analyticsProvider.logEvent("switch_to_translations", params)
  }

  private fun getScreenMode(
    isDualPages: Boolean,
    showingTranslations: Boolean,
    isSplitScreen: Boolean
  ): String {
    return when {
      isDualPages && showingTranslations && isSplitScreen -> "split_quran_translation"
      isDualPages && showingTranslations -> "dual_translations"
      isDualPages -> "dual_quran"
      showingTranslations -> "translation"
      else -> "quran"
    }
  }

  enum class AudioPlaybackSource { PAGE, AYAH }
}
