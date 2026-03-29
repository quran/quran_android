package com.quran.mobile.feature.qarilist.fakes

import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoSource
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQariDownloadInfoSource : QariDownloadInfoSource {
  // MutableStateFlow is intentional: the real QariDownloadInfoManager maintains a
  // persistent current state (StateFlow) that replays the latest download info to
  // new subscribers — matching StateFlow's replay-1 semantics.
  private val internalFlow = MutableStateFlow<List<QariDownloadInfo>>(emptyList())

  fun emit(items: List<QariDownloadInfo>) {
    internalFlow.value = items
  }

  override fun downloadQariInfoFilteringNonDownloadedGappedQaris(): Flow<List<QariDownloadInfo>> {
    return internalFlow
  }
}
