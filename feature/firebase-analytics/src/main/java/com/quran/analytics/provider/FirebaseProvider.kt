package com.quran.analytics.provider

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.quran.analytics.AnalyticsProvider
import com.quran.mobile.feature.firebase_analytics.BuildConfig
import javax.inject.Inject

class FirebaseProvider @Inject constructor(): AnalyticsProvider {
  private val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

  override fun logEvent(name: String, params: Map<String, Any>) {
    val bundle = Bundle()
    params.forEach { (key, value) ->
      when (value) {
        is Float -> bundle.putFloat(key, value)
        is Int -> bundle.putInt(key, value)
        is Long -> bundle.getLong(key, value)
        is String -> bundle.putString(key, value)
      }
    }

    @Suppress("KotlinConstantConditions")
    if (!BuildConfig.DEBUG) {
      firebaseAnalytics.logEvent(name, bundle)
    }
  }
}
