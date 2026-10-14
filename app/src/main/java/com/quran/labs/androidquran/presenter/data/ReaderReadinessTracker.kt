package com.quran.labs.androidquran.presenter.data

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class ReaderReadinessTracker @Inject constructor() {
  @Volatile
  private var readyPageType: String? = null

  fun markReady(pageType: String) {
    readyPageType = pageType
  }

  fun isReady(pageType: String?): Boolean {
    return pageType != null && readyPageType == pageType
  }
}
