package com.quran.labs.androidquran.common.audio.cache

import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import kotlinx.coroutines.flow.Flow

interface QariDownloadInfoSource {
  fun downloadedQariInfo(): Flow<List<QariDownloadInfo>>
  fun downloadQariInfoFilteringNonDownloadedGappedQaris(): Flow<List<QariDownloadInfo>>
}
