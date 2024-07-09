package com.quran.labs.androidquran.service.download

import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import java.io.File
import java.util.Locale

class GappedDownloadStrategy(
  private val startAyah: SuraAyah,
  private val endAyah: SuraAyah,
  private val audioUrlFormat: String,
  private val quranInfo: QuranInfo,
  private val destination: String,
  private val notifier: QuranDownloadNotifier,
  private val details: NotificationDetails,
  private val downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
) : DownloadStrategy {

  override fun fileCount(): Int {
    return if (startAyah.sura == endAyah.sura) {
      endAyah.ayah - startAyah.ayah + 1
    } else {
      val ayatInMiddleSuras = ((startAyah.sura + 1)..<endAyah.sura)
        .sumOf { quranInfo.getNumberOfAyahs(it) }
      val ayatInStartAyah = quranInfo.getNumberOfAyahs(startAyah.sura) - startAyah.ayah + 1
      ayatInStartAyah + ayatInMiddleSuras + endAyah.ayah
    } + 1 // always add 1 for basmallah, even if we don't need it for this play range
  }

  override fun downloadFiles(): Boolean {
    val extension = audioUrlFormat.substringAfterLast(".")
    for (sura in startAyah.sura..endAyah.sura) {
      details.sura = sura
      val ayahStart = if (sura == startAyah.sura) startAyah.ayah else 1
      val ayahEnd = if (sura == endAyah.sura) endAyah.ayah else quranInfo.getNumberOfAyahs(sura)

      val destDir = destination + File.separator + sura + File.separator
      File(destDir).mkdirs()
      for (ayah in ayahStart..ayahEnd) {
        details.ayah = ayah
        val url = audioUrlFormat.format(Locale.US, sura, ayah)
        val filename = "$ayah$extension"
        val file = File(destDir, filename)
        if (file.exists() || downloaderLambda(url, destDir, filename, details)) {
          notifier.notifyFileDownloaded(details, filename)
          details.currentFile++
        } else {
          return false
        }
      }
    }


    // attempt to download basmallah if it doesn't exist
    val destDir = destination + File.separator + 1 + File.separator
    File(destDir).mkdirs()
    val basmallah = File(destDir, "1$extension")
    val url = audioUrlFormat.format(Locale.US, 1, 1)
    val destFile = "1$extension"
    return if (basmallah.exists() || downloaderLambda(url, destDir, destFile, details)) {
      notifier.notifyFileDownloaded(details, "1$extension")
      details.currentFile++
      true
    } else {
      false
    }
  }
}
