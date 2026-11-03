package com.quran.labs.androidquran.widget

import android.content.Context
import android.content.Intent
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.util.AudioFileUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import java.io.File

/**
 * Builds an [AudioRequest] for an [AudioWidgetConfig].
 *
 * Unlike playback from the reader, the widget cannot prompt for downloads, so it
 * plays local files when they are all present and streams otherwise.
 */
class AudioWidgetRequestFactory @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val qariUtil: QariUtil,
  private val audioUtils: AudioUtils,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val audioFileUtils: AudioFileUtils
) {

  sealed interface Result {
    data class Ready(val audioRequest: AudioRequest) : Result

    /** gapless qaris need their timing database before anything can be played */
    data class MissingTimingDatabase(val downloadIntent: Intent) : Result
    data object Unavailable : Result
  }

  fun qari(qariId: Int): QariItem? {
    return qariUtil.getQariList(appContext).firstOrNull { it.id == qariId }
  }

  fun build(config: AudioWidgetConfig): Result {
    val qari = qari(config.qariId) ?: return Result.Unavailable
    val localPath = audioUtils.getLocalQariUrl(qari) ?: return Result.Unavailable

    val databasePath = audioUtils.getQariDatabasePathIfGapless(qari)
    if (databasePath != null && !File(databasePath).exists()) {
      val databaseUrl = audioFileUtils.gaplessDatabaseRootUrl + "/" +
          qari.databaseName + AudioUtils.ZIP_EXTENSION
      val downloadIntent = ServiceIntentHelper.getAudioDownloadIntent(
        appContext, databaseUrl, localPath, appContext.getString(R.string.timing_database)
      )
      return Result.MissingTimingDatabase(downloadIntent)
    }

    val (start, end) = if (config.start <= config.end) {
      config.start to config.end
    } else {
      config.end to config.start
    }

    val extension = audioExtensionDecider.audioExtensionForQari(qari)
    val allowedExtensions = audioExtensionDecider.allowedAudioExtensions(qari)
    val localUrlFormat = if (databasePath == null) {
      localPath + File.separator + "%d" + File.separator + "%d" + ".$extension"
    } else {
      localPath + File.separator + "%03d" + ".$extension"
    }

    val haveAllFiles = audioUtils.haveAllFiles(
      localUrlFormat, localPath, start, end, qari.isGapless, allowedExtensions
    ) && !audioUtils.shouldDownloadBasmallah(
      localPath, start, end, qari.isGapless, allowedExtensions
    )
    val urlFormat = if (haveAllFiles) {
      localUrlFormat
    } else {
      audioUtils.getQariUrl(qari, extension)
    }

    return Result.Ready(
      AudioRequest(
        start = start,
        end = end,
        qari = qari,
        repeatInfo = config.verseRepeat,
        rangeRepeatInfo = config.rangeRepeat,
        enforceBounds = config.enforceBounds,
        playbackSpeed = config.playbackSpeed,
        shouldStream = !haveAllFiles,
        audioPathInfo = AudioPathInfo(urlFormat, localPath, databasePath, allowedExtensions)
      )
    )
  }
}
