package com.quran.mobile.common.download

import com.quran.data.model.audio.Qari

interface Downloader {
  fun downloadSura(qari: Qari, sura: Int)
  fun downloadSuras(qari: Qari, startSura: Int, endSura: Int)
  fun cancelDownloads()
}
