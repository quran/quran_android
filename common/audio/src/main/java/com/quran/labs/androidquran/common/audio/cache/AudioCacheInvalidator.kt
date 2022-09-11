package com.quran.labs.androidquran.common.audio.cache

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCacheInvalidator @Inject constructor() {
  private val cache = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun qarisToInvalidate(): Flow<Int> = cache.filter { it > -1 }

  fun invalidateCacheForQari(qariId: Int) {
    cache.tryEmit(qariId)
  }
}
