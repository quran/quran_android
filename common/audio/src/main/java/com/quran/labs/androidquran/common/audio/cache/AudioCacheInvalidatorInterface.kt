package com.quran.labs.androidquran.common.audio.cache

import kotlinx.coroutines.flow.Flow

interface AudioCacheInvalidatorInterface {
  fun qarisToInvalidate(): Flow<Int>
  fun invalidateCacheForQari(qariId: Int)
}
