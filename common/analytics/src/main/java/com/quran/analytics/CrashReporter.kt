package com.quran.analytics

interface CrashReporter {
  fun log(message: String)
  fun recordException(throwable: Throwable)
}
