package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoSource
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQariDownloadInfoSource : QariDownloadInfoSource {
  private val downloadedFlow = MutableStateFlow<List<QariDownloadInfo>>(emptyList())

  fun emit(items: List<QariDownloadInfo>) {
    downloadedFlow.value = items
  }

  override fun downloadedQariInfo(): Flow<List<QariDownloadInfo>> = downloadedFlow

  override fun downloadQariInfoFilteringNonDownloadedGappedQaris(): Flow<List<QariDownloadInfo>> = downloadedFlow
}
