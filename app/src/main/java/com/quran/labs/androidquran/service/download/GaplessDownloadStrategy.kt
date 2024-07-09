package com.quran.labs.androidquran.service.download

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import timber.log.Timber
import java.io.File
import java.util.Locale

class GaplessDownloadStrategy(
  private val startAyah: SuraAyah,
  private val endAyah: SuraAyah,
  private val audioUrlFormat: String,
  private val destination: String,
  private val notifier: QuranDownloadNotifier,
  private val details: NotificationDetails,
  private val databaseUrl: String?,
  private val downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
) : DownloadStrategy {

  override fun fileCount(): Int {
    val lastSura = if (endAyah.ayah == 0) endAyah.sura - 1 else endAyah.sura
    return (lastSura - startAyah.sura) + 1 + if (databaseUrl == null) 0 else 1
  }

  override fun downloadFiles(): Boolean {
    val destDir = destination + File.separator
    File(destDir).mkdirs()

    for (sura in startAyah.sura..endAyah.sura) {
      details.sura = sura
      if (sura == endAyah.sura && endAyah.ayah == 0) {
        continue
      }

      val url = String.format(Locale.US, audioUrlFormat, sura)
      Timber.d("gapless asking to download %s to %s", url, destDir)
      val filename = url.substringAfterLast("/")
      val file = File(destDir, filename)
      if (file.exists() || downloaderLambda(url, destDir, filename, details)) {
        notifier.notifyFileDownloaded(details, filename)
        details.currentFile++
      } else {
        return false
      }
    }

    // download the database if requested
    if (databaseUrl != null) {
      Timber.d("gapless downloading database %s to %s", databaseUrl, destDir);
      val filename = databaseUrl.substringAfterLast("/")
      val file = File(destDir, filename)
      return if (file.exists() || downloaderLambda(databaseUrl, destDir, filename, details)) {
        notifier.notifyFileDownloaded(details, filename)
        details.currentFile++
        true
      } else {
        false
      }
    }
    return true
  }
}
