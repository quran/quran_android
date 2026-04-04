package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.data.model.audio.Qari
import com.quran.mobile.common.download.Downloader

data class DownloadCompleteSurasCall(
  val qari: Qari,
  val suras: List<Int>,
  val downloadDatabase: Boolean
)

class FakeDownloader : Downloader {
  val downloadCompleteSurasCalls = mutableListOf<DownloadCompleteSurasCall>()
  val downloadAudioDatabaseCalls = mutableListOf<Qari>()
  var cancelDownloadsCalled = 0

  override fun downloadCompleteSuras(qari: Qari, suras: List<Int>, downloadDatabase: Boolean) {
    downloadCompleteSurasCalls.add(DownloadCompleteSurasCall(qari, suras, downloadDatabase))
  }

  override fun downloadAudioDatabase(qari: Qari) {
    downloadAudioDatabaseCalls.add(qari)
  }

  override fun cancelDownloads() {
    cancelDownloadsCalled++
  }
}
