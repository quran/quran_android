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
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.File

class AudioPresenter @Inject
constructor(
  private val quranDisplayData: QuranDisplayData,
  private val audioUtil: AudioUtils,
  private val audioExtensionDecider: AudioExtensionDecider,
  private val quranFileUtils: QuranFileUtils
) : Presenter<PagerActivity> {
  private var pagerActivity: PagerActivity? = null
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

      val streamingUrl = audioUtil.getQariUrl(
        qari,
        audioExtensionDecider.audioExtensionForQari(qari)
      )

      // always keep local url format as primary so AudioQueue tries local
      // files first, and attach the streaming url as fallback for files
      // that aren't downloaded yet.
      val audioPath = audioPathInfo.copy(streamingUrlFormat = streamingUrl)

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
    pagerActivity?.let {
      // check for required prerequisite downloads (databases) that must complete
      // before playback can begin
      val prerequisiteIntent = getPrerequisiteDownloadIntent(it, audioRequest)
      if (prerequisiteIntent != null) {
        if (bypassChecks) {
          it.proceedWithDownload(prerequisiteIntent)
        } else {
          it.handleRequiredDownload(prerequisiteIntent)
        }
        return@let
      }

      if (audioRequest.shouldStream) {
        // start playback immediately via streaming, and also trigger a
        // background download so future ayahs can play from local files
        it.handlePlayback(audioRequest)
        val audioDownloadIntent = getAudioDownloadIntent(it, audioRequest)
        if (audioDownloadIntent != null) {
          it.startBackgroundAudioDownload(audioDownloadIntent)
        }
      } else {
        // not streaming - check if audio files need to be downloaded first
        val audioDownloadIntent = getAudioDownloadIntent(it, audioRequest)
        if (audioDownloadIntent != null) {
          if (bypassChecks) {
            it.proceedWithDownload(audioDownloadIntent)
          } else {
            it.handleRequiredDownload(audioDownloadIntent)
          }
        } else {
          it.handlePlayback(audioRequest)
        }
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
    lastAudioRequest?.let { request ->
      // if the last request was streaming, playback is already active and
      // AudioQueue will automatically resolve to local files as they are
      // downloaded, so we don't need to restart playback.
      if (!request.shouldStream) {
        play(request)
      }
    }
  }

  /**
   * Returns a download intent for prerequisite files (highlighting database,
   * gapless timing database) that must be downloaded before playback can start.
   */
  private fun getPrerequisiteDownloadIntent(context: Context, request: AudioRequest): Intent? {
    val qari = request.qari
    val audioPathInfo = request.audioPathInfo
    val path = audioPathInfo.localDirectory
    val gaplessDb = audioPathInfo.gaplessDatabase

    return if (!quranFileUtils.haveAyaPositionFile()) {
      getDownloadIntent(
        context,
        quranFileUtils.ayaPositionFileUrl,
        quranFileUtils.quranAyahDatabaseDirectory.absolutePath,
        context.getString(R.string.highlighting_database)
      )
    } else if (gaplessDb != null && !File(gaplessDb).exists()) {
      getDownloadIntent(
        context,
        getGaplessDatabaseUrl(qari)!!,
        path,
        context.getString(R.string.timing_database)
      )
    } else {
      null
    }
  }

  /**
   * Returns a download intent for audio files (basmallah and/or ayah audio)
   * that are not yet downloaded locally.
   */
  private fun getAudioDownloadIntent(context: Context, request: AudioRequest): Intent? {
    val qari = request.qari
    val audioPathInfo = request.audioPathInfo
    val path = audioPathInfo.localDirectory

    return if (audioUtil.shouldDownloadBasmallah(
        path,
        request.start,
        request.end,
        qari.isGapless,
        audioExtensionDecider.allowedAudioExtensions(qari)
      )
    ) {
      val title = quranDisplayData.getNotificationTitle(
        context, request.start, request.start, qari.isGapless
      )
      getDownloadIntent(
        context,
        audioUtil.getQariUrl(qari, audioExtensionDecider.audioExtensionForQari(qari)),
        path,
        title
      ).apply {
        putExtra(QuranDownloadService.EXTRA_START_VERSE, request.start)
        putExtra(QuranDownloadService.EXTRA_END_VERSE, request.start)
      }
    } else if (!haveAllFiles(audioPathInfo, request.start, request.end)) {
      val title = quranDisplayData.getNotificationTitle(
        context, request.start, request.end, qari.isGapless
      )
      getDownloadIntent(
        context,
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

  private fun getDownloadIntent(
    context: Context,
    url: String,
    destination: String,
    title: String
  ): Intent {
    return ServiceIntentHelper.getAudioDownloadIntent(context, url, destination, title)
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

  override fun bind(what: PagerActivity) {
    pagerActivity = what
  }

  override fun unbind(what: PagerActivity) {
    if (pagerActivity == what) {
      pagerActivity = null
    }
  }
}
