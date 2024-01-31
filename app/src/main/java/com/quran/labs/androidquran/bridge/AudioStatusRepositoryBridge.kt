package com.quran.labs.androidquran.bridge

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.ui.listener.AudioBarListener
import com.quran.labs.androidquran.ui.listener.AudioBarRecitationListener
import com.quran.mobile.feature.audiobar.presenter.AudioBarEventRepository
import com.quran.mobile.feature.audiobar.state.AudioBarEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AudioStatusRepositoryBridge(
  audioStatusRepository: AudioStatusRepository,
  audioBarEventRepository: AudioBarEventRepository,
  onPlaybackAyahChanged: ((SuraAyah?) -> Unit),
  audioBarListener: AudioBarListener,
  audioBarRecitationListener: AudioBarRecitationListener
) {

  private val scope = MainScope()
  private val audioPlaybackAyahFlow = audioStatusRepository.audioPlaybackFlow

  init {
    audioPlaybackAyahFlow
      .filterIsInstance<AudioStatus.Playback>()
      .onEach { status -> onPlaybackAyahChanged(status.currentAyah) }
      .launchIn(scope)

    audioBarEventRepository
      .audioBarEventFlow
      .onEach { event ->
        when (event) {
          AudioBarEvent.Acknowledge -> audioBarListener.onAcceptPressed()
          AudioBarEvent.Cancel -> audioBarListener.onCancelPressed(false)
          AudioBarEvent.CancelDownload -> audioBarListener.onCancelPressed(true)
          AudioBarEvent.ChangeQari -> audioBarListener.onShowQariList()
          AudioBarEvent.FastForward -> audioBarListener.onNextPressed()
          AudioBarEvent.Pause -> audioBarListener.onPausePressed()
          AudioBarEvent.Play -> audioBarListener.onPlayPressed()
          AudioBarEvent.ResumePlayback -> audioBarListener.onContinuePlaybackPressed()
          AudioBarEvent.Rewind -> audioBarListener.onPreviousPressed()
          is AudioBarEvent.SetRepeat -> audioBarListener.setRepeatCount(event.repeat)
          is AudioBarEvent.SetSpeed -> audioBarListener.setPlaybackSpeed(event.speed)
          AudioBarEvent.ShowSettings -> audioBarListener.onAudioSettingsPressed()
          AudioBarEvent.Stop -> audioBarListener.onStopPressed()
          AudioBarEvent.EndSession -> audioBarRecitationListener.onEndRecitationSessionPressed()
          AudioBarEvent.HideVerses -> audioBarRecitationListener.onHideVersesPressed()
          AudioBarEvent.PauseRecitation -> audioBarRecitationListener.onPauseRecitationPressed()
          AudioBarEvent.PlayRecitation -> audioBarRecitationListener.onPlayRecitationPressed()
          AudioBarEvent.Recitation, AudioBarEvent.Record -> audioBarRecitationListener.onRecitationPressed()
          AudioBarEvent.RecitationLongPress -> audioBarRecitationListener.onRecitationLongPressed()
          AudioBarEvent.Transcript -> audioBarRecitationListener.onRecitationTranscriptPressed()
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
