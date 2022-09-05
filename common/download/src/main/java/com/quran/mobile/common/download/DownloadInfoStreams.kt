package com.quran.mobile.common.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams of information about ongoing downloads
 * These streams should replace the usage of broadcasts for conveying information about
 * download status throughout the app.
 */
@Singleton
class DownloadInfoStreams @Inject constructor() {
  private val downloadInfoStream = MutableStateFlow<DownloadInfo?>(null)

  fun emitEvent(downloadInfo: DownloadInfo) {
    downloadInfoStream.value = downloadInfo
  }

  fun downloadInfoStream(): Flow<DownloadInfo> = downloadInfoStream.filterNotNull()
}
