package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidatorInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAudioCacheInvalidator : AudioCacheInvalidatorInterface {
  val invalidatedQariIds = mutableListOf<Int>()
  private val invalidationFlow = MutableSharedFlow<Int>()

  override fun invalidateCacheForQari(qariId: Int) {
    invalidatedQariIds.add(qariId)
  }

  override fun qarisToInvalidate(): Flow<Int> = invalidationFlow
}
