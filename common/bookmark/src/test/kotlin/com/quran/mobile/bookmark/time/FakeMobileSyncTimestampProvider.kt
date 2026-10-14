@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.time

import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.toPlatform
import kotlin.time.Instant

class FakeMobileSyncTimestampProvider(
  var timestampSeconds: Long = 1_000
) : MobileSyncTimestampProvider {
  override fun now(): PlatformDateTime {
    return Instant.fromEpochSeconds(timestampSeconds).toPlatform()
  }
}
