package com.quran.analytics.provider

import com.quran.analytics.CrashReporter
import dev.zacsweers.metro.Inject

class NoopCrashReporter @Inject constructor() : CrashReporter {
  override fun log(message: String) {
  }

  override fun recordException(throwable: Throwable) {
  }
}
