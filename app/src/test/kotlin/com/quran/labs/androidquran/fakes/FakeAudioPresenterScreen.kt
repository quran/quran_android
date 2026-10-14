package com.quran.labs.androidquran.fakes

import android.content.Intent
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenterScreen

class FakeAudioPresenterScreen : AudioPresenterScreen {
  val handlePlaybackCalls: MutableList<AudioRequest?> = mutableListOf()
  val handleRequiredDownloadCalls: MutableList<Intent?> = mutableListOf()
  val proceedWithDownloadCalls: MutableList<Intent?> = mutableListOf()
  val stringResponses: MutableMap<Int, String> = mutableMapOf()

  override fun handlePlayback(request: AudioRequest?) {
    handlePlaybackCalls.add(request)
  }

  override fun handleRequiredDownload(downloadIntent: Intent?) {
    handleRequiredDownloadCalls.add(downloadIntent)
  }

  override fun proceedWithDownload(downloadIntent: Intent?) {
    proceedWithDownloadCalls.add(downloadIntent)
  }

  override fun getString(resId: Int): String = stringResponses[resId] ?: ""
}
