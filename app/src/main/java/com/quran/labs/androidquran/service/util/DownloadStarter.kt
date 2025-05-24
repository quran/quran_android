package com.quran.labs.androidquran.service.util

import android.content.Context
import android.content.Intent
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class DownloadStarter @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val fileManager: QuranFileManager,
  private val audioUtils: AudioUtils,
  private val audioExtensionDecider: AudioExtensionDecider
) : Downloader {

  /**
   * This handles downloading a set of suras.
   *
   * This assumes that, even for gapped audio, the entire sura will be downloaded.
   */
  override fun downloadCompleteSuras(qari: Qari, suras: List<Int>, downloadDatabase: Boolean) {
    val basePath = fileManager.audioFileDirectory()
    val baseUri = basePath + qari.path
    val isGapless = qari.isGapless
    val sheikhName = appContext.getString(qari.nameResource)
    val extension = audioExtensionDecider.audioExtensionForQari(qari)

    val intent = ServiceIntentHelper.getDownloadIntent(
      appContext,
      audioUtils.getQariUrl(qari, extension),
      baseUri,
      sheikhName,
      AUDIO_DOWNLOAD_KEY + qari.id + suras.first(),
      QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    ).apply {
      putExtra(QuranDownloadService.EXTRA_AUDIO_BATCH, suras.toIntArray())
      putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless)
      putExtra(QuranDownloadService.EXTRA_METADATA, AudioDownloadMetadata(qari.id))
      if (downloadDatabase && isGapless) {
        putExtra(QuranDownloadService.EXTRA_DOWNLOAD_DATABASE, fileManager.urlForDatabase(qari))
      }
    }
    appContext.startService(intent)
  }

  override fun downloadAudioDatabase(qari: Qari) {
    val databaseUri = fileManager.urlForDatabase(qari)
    val intent = ServiceIntentHelper.getAudioDownloadIntent(
      appContext,
      databaseUri,
      fileManager.audioFileDirectory() + qari.path,
      appContext.getString(com.quran.mobile.feature.downloadmanager.R.string.audio_manager_database)
    ).apply {
      putExtra(QuranDownloadService.EXTRA_METADATA, AudioDownloadMetadata(qari.id))
    }
    appContext.startService(intent)
  }

  override fun cancelDownloads() {
    val intent = Intent(appContext, QuranDownloadService::class.java).apply {
      action = QuranDownloadService.ACTION_CANCEL_DOWNLOADS
    }
    appContext.startService(intent)
  }

  companion object {
    private const val AUDIO_DOWNLOAD_KEY = "AudioDownload.DownloadKey."
  }
}
