package com.quran.analytics

fun interface AnalyticsProvider {
  fun logEvent(name: String, params: Map<String, Any>)
}
