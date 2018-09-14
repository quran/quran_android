package com.quran.labs.androidquran.presenter.audio

import android.content.Context
import android.content.Intent
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QariItem
import com.quran.labs.androidquran.dao.audio.AudioRequest
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import java.io.File
import javax.inject.Inject

class AudioPresenter @Inject
constructor(private val quranInfo: QuranInfo,
            private val audioUtil: AudioUtils,
            private val quranFileUtils: QuranFileUtils) : Presenter<PagerActivity> {
  private var pagerActivity: PagerActivity? = null
  private var lastAudioRequest: AudioRequest? = null

  fun play(start: SuraAyah,
           end: SuraAyah,
           qari: QariItem,
           verseRepeat: Int,
           rangeRepeat: Int,
           enforceRange: Boolean,
           shouldStream: Boolean) {
    // override streaming if all the files are already downloaded
    val stream = if (shouldStream) { !haveAllFiles(qari, start, end) } else { false }

    val audioRequest = AudioRequest(
        start, end, qari, verseRepeat, rangeRepeat, enforceRange, stream)
    play(audioRequest)
  }

  fun play(audioRequest: AudioRequest) {
    lastAudioRequest = audioRequest
    pagerActivity?.let {
      val path = audioUtil.getLocalQariUrl(it, audioRequest.qari)
      if (path != null) {
        val downloadIntent = getDownloadIntent(it, path, audioRequest)
        if (downloadIntent != null) {
          it.handleRequiredDownload(downloadIntent)
        } else {
          // play the audio
          it.handlePlayback(audioRequest)
        }
      } else {
        // no mounted directory to put the files, can't download / play
      }
    }
  }

  fun onDownloadPermissionGranted() {
    lastAudioRequest?.let { play(it) }
  }

  fun onDownloadSuccess() {
    lastAudioRequest?.let { play(it) }
  }

  private fun getDownloadIntent(context: Context, path: String, request: AudioRequest): Intent? {
    val qari = request.qari
    val gaplessDb = audioUtil.getQariDatabasePathIfGapless(context, qari)

    return if (!quranFileUtils.haveAyaPositionFile(context)) {
      getDownloadIntent(context,
          quranFileUtils.ayaPositionFileUrl,
          quranFileUtils.getQuranDatabaseDirectory(context),
          context.getString(R.string.highlighting_database))
    } else if (gaplessDb != null && !File(gaplessDb).exists()) {
      getDownloadIntent(context,
          audioUtil.getGaplessDatabaseUrl(qari)!!,
          path,
          context.getString(R.string.timing_database))
    } else if (!request.shouldStream &&
        audioUtil.shouldDownloadBasmallah(path,
            request.start,
            request.end,
            qari.isGapless)) {
      val title = quranInfo.getNotificationTitle(
          context, request.start, request.start, qari.isGapless)
      getDownloadIntent(context, audioUtil.getQariUrl(qari), path, title).apply {
        putExtra(QuranDownloadService.EXTRA_START_VERSE, request.start)
        putExtra(QuranDownloadService.EXTRA_END_VERSE, request.start)
      }
    } else if (!request.shouldStream &&
        !haveAllFiles(qari, request.start, request.end)) {
      val title = quranInfo.getNotificationTitle(
          context, request.start, request.end, qari.isGapless)
      getDownloadIntent(context, audioUtil.getQariUrl(qari), path, title).apply {
        putExtra(QuranDownloadService.EXTRA_START_VERSE, request.start)
        putExtra(QuranDownloadService.EXTRA_END_VERSE, request.end)
        putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, qari.isGapless)
      }
    } else {
      null
    }
  }

  private fun getDownloadIntent(context: Context,
                                url: String,
                                destination: String,
                                title: String): Intent {
    return ServiceIntentHelper.getAudioDownloadIntent(context, url, destination, title)
  }

  private fun haveAllFiles(qari: QariItem, start: SuraAyah, end: SuraAyah): Boolean {
    pagerActivity?.let {
      val localPath = audioUtil.getLocalQariUrl(it, qari)
      if (localPath != null) {
        val databasePath = audioUtil.getQariDatabasePathIfGapless(it, qari)
        val baseUrl = if (databasePath.isNullOrEmpty()) {
          localPath + File.separator + "%d" + File.separator +
              "%d" + AudioUtils.AUDIO_EXTENSION
        } else {
          localPath + File.separator + "%03d" + AudioUtils.AUDIO_EXTENSION
        }
        return audioUtil.haveAllFiles(baseUrl, localPath, start, end, qari.isGapless)
      }
    }
    return false
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
