package com.quran.labs.androidquran.service.download

import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import timber.log.Timber
import java.io.File

class SingleFileDownloadStrategy(
  private val urlString: String,
  private val destination: String,
  private val outputFile: String,
  private val details: NotificationDetails,
  private val notifier: QuranDownloadNotifier,
  private val downloaderLambda: (String, String, String, NotificationDetails) -> Boolean
) : DownloadStrategy {
  override fun fileCount(): Int = 1

  override fun downloadFiles(): Boolean {
    File(destination).mkdirs()
    Timber.d("made directory %s", destination)
    return if (File(destination, outputFile).exists() ||
      downloaderLambda(urlString, destination, outputFile, details)
    ) {
      notifier.notifyFileDownloaded(details, outputFile);
      true
    } else {
      false
    }
  }
}
