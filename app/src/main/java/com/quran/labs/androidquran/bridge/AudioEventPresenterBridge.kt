package com.quran.labs.androidquran.bridge

import com.quran.data.model.SuraAyah
import com.quran.reading.common.AudioEventPresenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AudioEventPresenterBridge constructor(
  audioEventPresenter: AudioEventPresenter,
  onPlaybackAyahChanged: ((SuraAyah?) -> Unit)
) {

  private val scope = MainScope()
  private val audioPlaybackAyahFlow = audioEventPresenter.audioPlaybackAyahFlow

  init {
    audioPlaybackAyahFlow
      .onEach { onPlaybackAyahChanged(it) }
      .launchIn(scope)
  }

  fun dispose() {
    scope.cancel()
  }
}
