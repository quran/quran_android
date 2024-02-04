package com.quran.labs.androidquran.feature.reading.bridge

import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class DownloadBridge @Inject constructor(private val downloadInfoStreams: DownloadInfoStreams) {
  private val scope = MainScope()

  fun subscribeToDownloads(onDownloadSuccess: () -> Unit) {
    downloadInfoStreams.downloadInfoStream()
      .filter { it is DownloadInfo.DownloadBatchSuccess }
      .onEach { onDownloadSuccess() }
      .launchIn(scope)
  }

  fun unsubscribe() {
    scope.cancel()
  }
}
