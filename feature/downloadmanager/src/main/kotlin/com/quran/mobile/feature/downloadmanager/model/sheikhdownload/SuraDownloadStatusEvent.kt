package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

sealed class SuraDownloadStatusEvent {
  data class Downloaded(val sura: Int): SuraDownloadStatusEvent()
  data class Canceled(val sura: Int): SuraDownloadStatusEvent()
  data class Progress(val sura: Int, val progress: Int): SuraDownloadStatusEvent()
}
