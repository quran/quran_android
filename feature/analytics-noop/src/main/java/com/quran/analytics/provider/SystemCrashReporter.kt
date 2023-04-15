package com.quran.analytics.provider

import com.quran.analytics.CrashReporter

object SystemCrashReporter {
  private val noopCrashReporter by lazy { NoopCrashReporter() }

  fun crashReporter(): CrashReporter = noopCrashReporter
}
