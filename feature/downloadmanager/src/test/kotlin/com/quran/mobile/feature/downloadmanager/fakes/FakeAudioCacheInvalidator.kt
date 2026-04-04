package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidator

class FakeAudioCacheInvalidator : AudioCacheInvalidator() {
  val invalidatedQariIds = mutableListOf<Int>()

  override fun invalidateCacheForQari(qariId: Int) {
    invalidatedQariIds.add(qariId)
  }
}
