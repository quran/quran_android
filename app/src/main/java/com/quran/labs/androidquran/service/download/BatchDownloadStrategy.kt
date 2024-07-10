package com.quran.labs.androidquran.service.download

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails

class BatchDownloadStrategy(
  private val audioUrlFormat: String,
  private val destination: String,
  private val isGapless: Boolean,
  private val quranInfo: QuranInfo,
  private val notifier: QuranDownloadNotifier,
  private val details: NotificationDetails,
  private val batch: IntArray,
  private val databaseUrl: String?,
  private val downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
) : DownloadStrategy {

  private val listStrategies: List<DownloadStrategy> by lazy {
    buildList {
      batch.forEachIndexed { index, sura ->
        add(strategyFor(sura, index == 0))
      }
    }
  }

  override fun fileCount(): Int {
    // unfortunately, we'll try to download the basmallah for each sura
    // when gapped, but the file check will stop us from actually hitting
    // the server.
    return listStrategies.sumOf { it.fileCount() }
  }

  override fun downloadFiles(): Boolean {
    return listStrategies.all { it.downloadFiles() }
  }

  private fun strategyFor(sura: Int, isFirst: Boolean): DownloadStrategy {
    val start = SuraAyah(sura, 1)
    val end = SuraAyah(sura, quranInfo.getNumberOfAyahs(sura))
    return if (isGapless) {
      GaplessDownloadStrategy(
        start,
        end,
        audioUrlFormat,
        destination,
        notifier,
        details,
        if (isFirst) databaseUrl else null,
        downloaderLambda
      )
    } else {
      GappedDownloadStrategy(
        start,
        end,
        audioUrlFormat,
        quranInfo,
        destination,
        notifier,
        details,
        downloaderLambda
      )
    }
  }
}
