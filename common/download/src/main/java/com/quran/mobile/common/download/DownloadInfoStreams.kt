package com.quran.mobile.common.download

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Streams of information about ongoing downloads
 * These streams should replace the usage of broadcasts for conveying information about
 * download status throughout the app.
 */
@SingleIn(AppScope::class)
class DownloadInfoStreams @Inject constructor() {
  private val downloadInfoStream =
    MutableSharedFlow<DownloadInfo>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun emitEvent(downloadInfo: DownloadInfo) {
    downloadInfoStream.tryEmit(downloadInfo)
  }

  fun requestDownloadNetworkPermission() {
    emitEvent(DownloadInfo.RequestDownloadNetworkPermission)
  }

  fun downloadRequested() {
    emitEvent(DownloadInfo.DownloadRequested)
  }

  fun downloadInfoStream(): Flow<DownloadInfo> = downloadInfoStream
}
