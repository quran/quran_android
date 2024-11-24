package com.quran.analytics.provider

import com.quran.analytics.CrashReporter

object SystemCrashReporter {
  private val firebaseCrashReporter by lazy { FirebaseCrashReporter() }

  fun crashReporter(): CrashReporter = firebaseCrashReporter
}
