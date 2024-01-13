package com.quran.labs.androidquran.bridge

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.view.AudioStatusBar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AudioStatusRepositoryBridge(
  audioStatusRepository: AudioStatusRepository,
  audioStatusBar: () -> AudioStatusBar,
  onPlaybackAyahChanged: ((SuraAyah?) -> Unit)
) {

  private val scope = MainScope()
  private val audioPlaybackAyahFlow = audioStatusRepository.audioPlaybackFlow

  init {
    audioPlaybackAyahFlow
      .onEach { status ->
        when (status) {
          is AudioStatus.Playback -> {
            val statusBar = audioStatusBar()
            if (status.playbackStatus == PlaybackStatus.PLAYING) {
              statusBar.switchMode(AudioStatusBar.PLAYING_MODE)
              if (status.audioRequest.repeatInfo >= -1) {
                statusBar.setRepeatCount(status.audioRequest.repeatInfo)
                statusBar.setSpeed(status.audioRequest.playbackSpeed)
              }
            } else if (status.playbackStatus == PlaybackStatus.PAUSED) {
              statusBar.switchMode(AudioStatusBar.PAUSED_MODE)
            } else if (status.playbackStatus == PlaybackStatus.PREPARING) {
              statusBar.switchMode(AudioStatusBar.LOADING_MODE)
            }
            onPlaybackAyahChanged(status.currentAyah)
          }
          AudioStatus.Stopped -> {
            audioStatusBar().switchMode(AudioStatusBar.STOPPED_MODE)
          }
        }
      }
      .launchIn(scope)
  }

  fun audioRequest(): AudioRequest? {
    return when (val status = audioPlaybackAyahFlow.value) {
      is AudioStatus.Playback -> status.audioRequest
      AudioStatus.Stopped -> null
    }
  }

  fun dispose() {
    scope.cancel()
  }
}
