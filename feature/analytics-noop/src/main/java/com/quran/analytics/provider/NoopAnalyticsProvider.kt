package com.quran.analytics.provider

import com.quran.analytics.AnalyticsProvider
import javax.inject.Inject

class NoopAnalyticsProvider @Inject constructor() : AnalyticsProvider {

  override fun logEvent(
    name: String,
    params: Map<String, Any>
  ) {
  }
}
