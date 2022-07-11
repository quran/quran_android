package com.quran.labs.androidquran.common.audio.cache

import com.quran.labs.androidquran.common.audio.model.QariDownloadInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class QariDownloadInfoCache @Inject constructor() {
  private val cache = MutableStateFlow<List<QariDownloadInfo>>(emptyList())

  fun flow(): Flow<List<QariDownloadInfo>> = cache

  fun writeAll(qariDownloads: List<QariDownloadInfo>) {
    cache.value = qariDownloads
  }
}
