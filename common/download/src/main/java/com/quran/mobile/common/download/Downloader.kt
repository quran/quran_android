package com.quran.mobile.common.download

import com.quran.data.model.audio.Qari

interface Downloader {
  fun downloadBatchSuras(qari: Qari, suras: List<Int>, downloadDatabase: Boolean)
  fun downloadSuras(qari: Qari, startSura: Int, endSura: Int, downloadDatabase: Boolean)
  fun downloadAudioDatabase(qari: Qari)
  fun cancelDownloads()
}
