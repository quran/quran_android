package com.quran.analytics.provider

import com.quran.analytics.CrashReporter
import javax.inject.Inject

class NoopCrashReporter @Inject constructor() : CrashReporter {
  override fun log(message: String) {
  }

  override fun recordException(throwable: Throwable) {
  }
}
