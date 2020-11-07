package com.quran.labs.androidquran.presenter.data

import com.quran.analytics.AnalyticsProvider
import com.quran.labs.androidquran.util.QuranSettings
import dagger.Reusable
import javax.inject.Inject

@Reusable
class QuranIndexEventLoggerImpl @Inject constructor(
  private val analyticsProvider: AnalyticsProvider,
  private val quranSettings: QuranSettings
): QuranIndexEventLogger {

  override fun logAnalytics() {
    val appLocation = quranSettings.appCustomLocation
    val pathType =
      when {
        appLocation == null -> "unknown"
        "com.quran" in appLocation -> "external"
        else -> "sdcard"
      }

    val params: Map<String, Any> = mapOf(
        "pathType" to pathType,
        "sortOrder" to quranSettings.bookmarksSortOrder,
        "groupByTags" to quranSettings.bookmarksGroupedByTags,
        "showRecents" to quranSettings.showRecents,
        "showDate" to quranSettings.showDate
    )

    analyticsProvider.logEvent("quran_index_view", params)
  }
}
