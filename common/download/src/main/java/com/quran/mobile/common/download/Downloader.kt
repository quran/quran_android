package com.quran.mobile.common.download

import com.quran.data.model.audio.Qari

interface Downloader {
  fun downloadCompleteSuras(qari: Qari, suras: List<Int>, downloadDatabase: Boolean)
  fun downloadAudioDatabase(qari: Qari)
  fun cancelDownloads()
}
