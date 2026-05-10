package com.quran.mobile.bookmark.time

private const val MILLIS_TIMESTAMP_THRESHOLD = 10_000_000_000L

internal fun Long.legacyTimestampMillis(): Long {
  // Legacy bookmark data can contain either epoch seconds or epoch milliseconds.
  return if (this > MILLIS_TIMESTAMP_THRESHOLD) this else this * 1000
}
