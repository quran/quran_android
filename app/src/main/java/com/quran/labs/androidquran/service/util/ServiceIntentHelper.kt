package com.quran.labs.androidquran.service.util

import android.content.Context
import android.content.Intent

import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.service.QuranDownloadService

object ServiceIntentHelper {
  private const val AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY"

  fun getAudioDownloadIntent(
    context: Context,
    url: String,
    destination: String,
    notificationTitle: String
  ): Intent {
    return getDownloadIntent(
      context, url, destination, notificationTitle,
      AUDIO_DOWNLOAD_KEY, QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
  }

  @JvmStatic
  fun getDownloadIntent(
    context: Context,
    url: String,
    destination: String?,
    notificationTitle: String,
    key: String,
    type: Int
  ): Intent {
    return Intent(context, QuranDownloadService::class.java).apply {
      putExtra(QuranDownloadService.EXTRA_URL, url)
      putExtra(QuranDownloadService.EXTRA_DESTINATION, destination)
      putExtra(QuranDownloadService.EXTRA_NOTIFICATION_NAME, notificationTitle)
      putExtra(QuranDownloadService.EXTRA_DOWNLOAD_KEY, key)
      putExtra(QuranDownloadService.EXTRA_DOWNLOAD_TYPE, type)
      action = QuranDownloadService.ACTION_DOWNLOAD_URL
    }
  }

  @JvmStatic
  fun getErrorResourceFromDownloadIntent(
    intent: Intent,
    willRetry: Boolean
  ): Int {
    val errorCode = intent.getIntExtra(QuranDownloadNotifier.ProgressIntent.ERROR_CODE, 0)
    return getErrorResourceFromErrorCode(errorCode, willRetry)
  }


  fun getErrorResourceFromErrorCode(
    errorCode: Int,
    willRetry: Boolean
  ): Int {
    return when (errorCode) {
      QuranDownloadNotifier.ERROR_DISK_SPACE -> R.string.download_error_disk
      QuranDownloadNotifier.ERROR_NETWORK -> {
        if (willRetry) {
          R.string.download_error_network_retry
        } else {
          R.string.download_error_network
        }
      }
      QuranDownloadNotifier.ERROR_PERMISSIONS -> R.string.download_error_perms
      QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD -> {
        if (willRetry) {
          R.string.download_error_invalid_download_retry
        } else {
          R.string.download_error_invalid_download
        }
      }
      QuranDownloadNotifier.ERROR_CANCELLED -> R.string.notification_download_canceled
      QuranDownloadNotifier.ERROR_GENERAL -> R.string.download_error_general
      else -> R.string.download_error_general
    }
  }
}
