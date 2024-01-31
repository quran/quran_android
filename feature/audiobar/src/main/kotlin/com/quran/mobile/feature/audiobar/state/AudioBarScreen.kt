package com.quran.mobile.feature.audiobar.state

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

@Parcelize
data object AudioBarScreen : Screen {
  sealed class AudioBarState : CircuitUiState {
    sealed class ActivePlayback : AudioBarState() {
      abstract val repeat: Int
      abstract val speed: Float
      abstract val eventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit
    }

    data class Playing(
      override val repeat: Int,
      override val speed: Float,
      override val eventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit,
      val playbackEventSink: (AudioBarUiEvent.PlayingPlaybackEvent) -> Unit
    ) : ActivePlayback()

    data class Paused(
      override val repeat: Int,
      override val speed: Float,
      override val eventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit,
      val pausedEventSink: (AudioBarUiEvent.PausedPlaybackEvent) -> Unit
    ) : ActivePlayback()

    data class Stopped(
      val qariNameResource: Int,
      val enableRecording: Boolean,
      val eventSink: (AudioBarUiEvent.StoppedPlaybackEvent) -> Unit
    ) : AudioBarState()

    data class Loading(
      val progress: Int,
      val messageResource: Int,
      val eventSink: (AudioBarUiEvent.LoadingPlaybackEvent) -> Unit
    ) : AudioBarState()

    data class Error(
      val messageResource: Int,
      val eventSink: (AudioBarUiEvent.ErrorPlaybackEvent) -> Unit
    ) : AudioBarState()

    data class Prompt(
      val messageResource: Int,
      val eventSink: (AudioBarUiEvent.PromptEvent) -> Unit
    ) : AudioBarState()

    sealed class RecitationState(val isRecitationActive: Boolean) : AudioBarState() {
      abstract val eventSink: (AudioBarUiEvent.CommonRecordingEvent) -> Unit
    }

    data class RecitationListening(
      override val eventSink: (AudioBarUiEvent.CommonRecordingEvent) -> Unit,
      val listeningEventSink: (AudioBarUiEvent.RecitationListeningEvent) -> Unit
    ) : RecitationState(true)

    data class RecitationPlaying(
      override val eventSink: (AudioBarUiEvent.CommonRecordingEvent) -> Unit,
      val playingEventSink: (AudioBarUiEvent.RecitationPlayingEvent) -> Unit
    ) : RecitationState(false)

    data class RecitationStopped(
      override val eventSink: (AudioBarUiEvent.CommonRecordingEvent) -> Unit,
      val stoppedEventSink: (AudioBarUiEvent.RecitationStoppedEvent) -> Unit
    ) : RecitationState(false)
  }

  sealed class AudioBarUiEvent(val audioBarEvent: AudioBarEvent) : CircuitUiEvent {
    sealed class CommonPlaybackEvent(audioBarEvent: AudioBarEvent) :
      AudioBarUiEvent(audioBarEvent) {
      data object Stop : CommonPlaybackEvent(AudioBarEvent.Stop)
      data object Rewind : CommonPlaybackEvent(AudioBarEvent.Rewind)
      data object FastForward : CommonPlaybackEvent(AudioBarEvent.FastForward)
      data class SetSpeed(val speed: Float) : CommonPlaybackEvent(AudioBarEvent.SetSpeed(speed))
      data class SetRepeat(val repeat: Int) : CommonPlaybackEvent(AudioBarEvent.SetRepeat(repeat))
      data object ShowSettings : CommonPlaybackEvent(AudioBarEvent.ShowSettings)
    }

    sealed class PlayingPlaybackEvent(audioBarEvent: AudioBarEvent) :
      AudioBarUiEvent(audioBarEvent) {
      data object Pause : PlayingPlaybackEvent(AudioBarEvent.Pause)
    }

    sealed class PausedPlaybackEvent(audioBarEvent: AudioBarEvent) :
      AudioBarUiEvent(audioBarEvent) {
      data object Play : PausedPlaybackEvent(AudioBarEvent.Play)
    }

    sealed class LoadingPlaybackEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
      data object Cancel : LoadingPlaybackEvent(AudioBarEvent.Cancel)
    }

    sealed class ErrorPlaybackEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
      data object Cancel : ErrorPlaybackEvent(AudioBarEvent.Cancel)
    }

    sealed class StoppedPlaybackEvent(audioBarEvent: AudioBarEvent) :
      AudioBarUiEvent(audioBarEvent) {
      data object ChangeQari : StoppedPlaybackEvent(AudioBarEvent.ChangeQari)
      data object Play : StoppedPlaybackEvent(AudioBarEvent.Play)
      data object Record : StoppedPlaybackEvent(AudioBarEvent.Record)
    }

    sealed class PromptEvent(audioBarEvent: AudioBarEvent) : AudioBarUiEvent(audioBarEvent) {
      data object Cancel : PromptEvent(AudioBarEvent.Cancel)
      data object Acknowledge : PromptEvent(AudioBarEvent.Acknowledge)
    }

    sealed class CommonRecordingEvent(audioBarEvent: AudioBarEvent) :
      AudioBarUiEvent(audioBarEvent) {
      data object Recitation : CommonRecordingEvent(AudioBarEvent.Recitation)
      data object RecitationLongPress : CommonRecordingEvent(AudioBarEvent.RecitationLongPress)
      data object Transcript : CommonRecordingEvent(AudioBarEvent.Transcript)
    }

    sealed class RecitationListeningEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
      data object HideVerses : RecitationListeningEvent(AudioBarEvent.HideVerses)
    }

    sealed class RecitationPlayingEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
      data object EndSession : RecitationPlayingEvent(AudioBarEvent.EndSession)
      data object PauseRecitation : RecitationPlayingEvent(AudioBarEvent.PauseRecitation)
    }

    sealed class RecitationStoppedEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
      data object EndSession : RecitationStoppedEvent(AudioBarEvent.EndSession)
      data object PlayRecitation : RecitationStoppedEvent(AudioBarEvent.PlayRecitation)
    }
  }
}
