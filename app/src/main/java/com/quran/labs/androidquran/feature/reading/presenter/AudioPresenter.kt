package com.quran.labs.androidquran.feature.reading.presenter

import android.content.Context
import android.content.Intent
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.labs.androidquran.data.QuranDisplayInterface
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.util.AudioFileUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.AudioUtilsInterface
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.File

class AudioPresenter @Inject
constructor(
  @ApplicationContext private val appContext: Context,
  private val quranDisplayData: QuranDisplayInterface,
  private val audioUtil: AudioUtilsInterface,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val quranFileUtils: AudioFileUtils
) : Presenter<AudioPresenterScreen> {
  private var pagerActivity: AudioPresenterScreen? = null
  private var lastAudioRequest: AudioRequest? = null

  fun play(
    start: SuraAyah,
    end: SuraAyah,
    qari: QariItem,
    verseRepeat: Int,
    rangeRepeat: Int,
    enforceRange: Boolean,
    playbackSpeed: Float,
    shouldStream: Boolean
  ) {
    val audioPathInfo = getLocalAudioPathInfo(qari)
    if (audioPathInfo != null) {
      // override streaming if all the files are already downloaded
      val stream = if (shouldStream) {
        !haveAllFiles(audioPathInfo, start, end)
      } else {
        false
      }

      // if we're still streaming, change the base qari format in audioPathInfo
      // to a remote url format (instead of a path to a local directory)
      val audioPath = if (stream) {
        audioPathInfo.copy(
          urlFormat = audioUtil.getQariUrl(
            qari,
            audioExtensionDecider.audioExtensionForQari(qari)
          )
        )
      } else {
        audioPathInfo
      }

      val (actualStart, actualEnd) = if (start <= end) {
        start to end
      } else {
        Timber.e(
          IllegalStateException(
            "End isn't larger than the start: $start to $end"
          )
        )
        end to start
      }

      val audioRequest = AudioRequest(
        actualStart,
        actualEnd,
        qari,
        verseRepeat,
        rangeRepeat,
        enforceRange,
        playbackSpeed,
        stream,
        audioPath
      )
      play(audioRequest)
    }
  }

  private fun play(audioRequest: AudioRequest) {
    lastAudioRequest = audioRequest
    proceedWithAudioRequest(audioRequest)
  }

  private fun proceedWithAudioRequest(audioRequest: AudioRequest, bypassChecks: Boolean = false) {
    pagerActivity?.let { screen ->
      val downloadIntent = getDownloadIntent(screen, audioRequest)
      if (downloadIntent != null) {
        if (bypassChecks) {
          screen.proceedWithDownload(downloadIntent)
        } else {
          screen.handleRequiredDownload(downloadIntent)
        }
      } else {
        // play the audio
        screen.handlePlayback(audioRequest)
      }
    }
  }

  fun onDownloadPermissionGranted() {
    lastAudioRequest?.let { play(it) }
  }

  fun onPostNotificationsPermissionResponse(granted: Boolean) {
    lastAudioRequest?.let { audioRequest ->
      proceedWithAudioRequest(audioRequest, true)
    }
  }

  fun onDownloadSuccess() {
    lastAudioRequest?.let { play(it) }
  }

  private fun getDownloadIntent(screen: AudioPresenterScreen, request: AudioRequest): Intent? {
    val qari = request.qari
    val audioPathInfo = request.audioPathInfo
    val path = audioPathInfo.localDirectory
    val gaplessDb = audioPathInfo.gaplessDatabase

    return if (!quranFileUtils.haveAyaPositionFile()) {
      buildDownloadIntent(
        quranFileUtils.ayaPositionFileUrl,
        quranFileUtils.quranAyahDatabaseDirectory.absolutePath,
        screen.getString(R.string.highlighting_database)
      )
    } else if (gaplessDb != null && !File(gaplessDb).exists()) {
      buildDownloadIntent(
        getGaplessDatabaseUrl(qari)!!,
        path,
        screen.getString(R.string.timing_database)
      )
    } else if (!request.shouldStream &&
      audioUtil.shouldDownloadBasmallah(
        path,
        request.start,
        request.end,
        qari.isGapless,
        audioExtensionDecider.allowedAudioExtensions(qari)
      )
    ) {
      val title = quranDisplayData.getNotificationTitle(
        appContext, request.start, request.start, qari.isGapless
      )
      buildDownloadIntent(
        audioUtil.getQariUrl(qari, audioExtensionDecider.audioExtensionForQari(qari)),
        path,
        title
      ).apply {
        putExtra(QuranDownloadService.EXTRA_START_VERSE, request.start)
        putExtra(QuranDownloadService.EXTRA_END_VERSE, request.start)
      }
    } else if (!request.shouldStream && !haveAllFiles(audioPathInfo, request.start, request.end)) {
      val title = quranDisplayData.getNotificationTitle(
        appContext, request.start, request.end, qari.isGapless
      )
      buildDownloadIntent(
        audioUtil.getQariUrl(qari, audioExtensionDecider.audioExtensionForQari(qari)),
        path,
        title
      ).apply {
        putExtra(QuranDownloadService.EXTRA_START_VERSE, request.start)
        putExtra(QuranDownloadService.EXTRA_END_VERSE, request.end)
        putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, qari.isGapless)
        putExtra(QuranDownloadService.EXTRA_METADATA, AudioDownloadMetadata(qari.id))
      }
    } else {
      null
    }
  }

  private fun buildDownloadIntent(
    url: String,
    destination: String,
    title: String
  ): Intent {
    return ServiceIntentHelper.getAudioDownloadIntent(appContext, url, destination, title)
  }

  private fun getLocalAudioPathInfo(qari: QariItem): AudioPathInfo? {
    pagerActivity?.let {
      val localPath = audioUtil.getLocalQariUrl(qari)
      if (localPath != null) {
        val databasePath = audioUtil.getQariDatabasePathIfGapless(qari)
        val extension = audioExtensionDecider.audioExtensionForQari(qari)
        val urlFormat = if (databasePath.isNullOrEmpty()) {
          localPath + File.separator + "%d" + File.separator + "%d" + ".$extension"
        } else {
          localPath + File.separator + "%03d" + ".$extension"
        }
        return AudioPathInfo(
          urlFormat, localPath, databasePath,
          audioExtensionDecider.allowedAudioExtensions(qari)
        )
      }
    }
    return null
  }

  private fun haveAllFiles(audioPathInfo: AudioPathInfo, start: SuraAyah, end: SuraAyah): Boolean {
    return audioUtil.haveAllFiles(
      audioPathInfo.urlFormat,
      audioPathInfo.localDirectory, start, end, audioPathInfo.gaplessDatabase != null,
      audioPathInfo.allowedExtensions
    )
  }

  private fun getGaplessDatabaseUrl(qari: QariItem): String? {
    if (!qari.isGapless || qari.databaseName == null) {
      return null
    }

    val dbName = qari.databaseName + AudioUtils.ZIP_EXTENSION
    return quranFileUtils.gaplessDatabaseRootUrl + "/" + dbName
  }

  override fun bind(what: AudioPresenterScreen) {
    pagerActivity = what
  }

  override fun unbind(what: AudioPresenterScreen) {
    if (pagerActivity == what) {
      pagerActivity = null
    }
  }
}
