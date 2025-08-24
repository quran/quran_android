package com.quran.labs.androidquran.service.download

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import java.io.File
import java.util.Locale

class GappedDownloadStrategy(
  private val ranges: List<VerseRange>,
  private val audioUrlFormat: String,
  private val quranInfo: QuranInfo,
  private val destination: String,
  private val notifier: QuranDownloadNotifier,
  private val details: NotificationDetails,
  private val downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
) : DownloadStrategy {

  constructor(
    startAyah: SuraAyah,
    endAyah: SuraAyah,
    audioUrlFormat: String,
    quranInfo: QuranInfo,
    destination: String,
    notifier: QuranDownloadNotifier,
    details: NotificationDetails,
    downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
  ) :
      this(
        ranges = listOf(startAyah.asVerseRangeTo(endAyah, quranInfo)),
        audioUrlFormat,
        quranInfo,
        destination,
        notifier,
        details,
        downloaderLambda
      )

  override fun fileCount(): Int {
    return ranges.sumOf { it.versesInRange } + 1 // +1 for basmallah
  }

  override fun downloadFiles(): Boolean {
    val extension = audioUrlFormat.substringAfterLast(".")
    for (range in ranges) {
      for (sura in range.startSura..range.endingSura) {
        details.sura = sura
        val ayahStart = if (sura == range.startSura) range.startAyah else 1
        val ayahEnd = if (sura == range.endingSura) range.endingAyah else quranInfo.getNumberOfAyahs(sura)

        val destDir = destination + File.separator + sura + File.separator
        File(destDir).mkdirs()
        for (ayah in ayahStart..ayahEnd) {
          details.ayah = ayah
          val url = audioUrlFormat.format(Locale.US, sura, ayah)
          val filename = "$ayah.$extension"
          val file = File(destDir, filename)
          if (file.exists() || downloaderLambda(url, destDir, filename, details)) {
            notifier.notifyFileDownloaded(details, filename)
            details.currentFile++
          } else {
            return false
          }
        }
      }
    }


    // attempt to download basmallah if it doesn't exist
    val destDir = destination + File.separator + 1 + File.separator
    File(destDir).mkdirs()
    val basmallah = File(destDir, "1.$extension")
    val url = audioUrlFormat.format(Locale.US, 1, 1)
    val destFile = "1.$extension"
    return if (basmallah.exists() || downloaderLambda(url, destDir, destFile, details)) {
      notifier.notifyFileDownloaded(details, "1.$extension")
      details.currentFile++
      true
    } else {
      false
    }
  }
}
