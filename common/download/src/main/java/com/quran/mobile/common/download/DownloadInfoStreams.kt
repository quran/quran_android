package com.quran.mobile.common.download

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams of information about ongoing downloads
 * These streams should replace the usage of broadcasts for conveying information about
 * download status throughout the app.
 */
@Singleton
class DownloadInfoStreams @Inject constructor() {
  private val downloadInfoStream =
    MutableSharedFlow<DownloadInfo>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun emitEvent(downloadInfo: DownloadInfo) {
    downloadInfoStream.tryEmit(downloadInfo)
  }

  fun downloadInfoStream(): Flow<DownloadInfo> = downloadInfoStream
}
