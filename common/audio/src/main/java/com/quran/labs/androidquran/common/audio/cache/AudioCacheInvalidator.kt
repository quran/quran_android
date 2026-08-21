package com.quran.labs.androidquran.common.audio.cache

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

@SingleIn(AppScope::class)
class AudioCacheInvalidator @Inject constructor() : AudioCacheInvalidatorInterface {
  private val cache = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun qarisToInvalidate(): Flow<Int> = cache.filter { it > -1 }

  override fun invalidateCacheForQari(qariId: Int) {
    cache.tryEmit(qariId)
  }
}
