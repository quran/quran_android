package com.quran.analytics.provider

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.quran.analytics.CrashReporter
import javax.inject.Inject

class FirebaseCrashReporter @Inject constructor() : CrashReporter {
  private val crashlytics = FirebaseCrashlytics.getInstance()

  override fun log(message: String) {
    crashlytics.log(message)
  }

  override fun recordException(throwable: Throwable) {
    crashlytics.recordException(throwable)
  }
}
