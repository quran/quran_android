@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.time

import com.quran.data.di.AppScope
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.fromPlatform
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

interface MobileSyncTimestampProvider {
  fun now(): PlatformDateTime

  fun nowEpochMillis(): Long {
    return now().fromPlatform().toEpochMilliseconds()
  }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMobileSyncTimestampProvider @Inject constructor() : MobileSyncTimestampProvider {
  override fun now(): PlatformDateTime {
    return Clock.System.now().toPlatform()
  }
}
