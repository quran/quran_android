package com.quran.mobile.feature.audiobar

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

sealed class AudioBarState : CircuitUiState {
  sealed class ActivePlayback : AudioBarState() {
    abstract val repeat: Int
    abstract val speed: Float
    abstract val eventSink: (AudioBarEvent.CommonPlaybackEvent) -> Unit
  }

  data class Playing(
    override val repeat: Int,
    override val speed: Float,
    override val eventSink: (AudioBarEvent.CommonPlaybackEvent) -> Unit,
    val playbackEventSink: (AudioBarEvent.PlayingPlaybackEvent) -> Unit
  ) : ActivePlayback()

  data class Paused(
    override val repeat: Int,
    override val speed: Float,
    override val eventSink: (AudioBarEvent.CommonPlaybackEvent) -> Unit,
    val pausedEventSink: (AudioBarEvent.PausedPlaybackEvent) -> Unit
  ) : ActivePlayback()

  data class Stopped(
    val qariName: String,
    val enableRecording: Boolean,
    val eventSink: (AudioBarEvent.StoppedPlaybackEvent) -> Unit
  ) : AudioBarState()

  data class Loading(
    val progress: Int,
    val message: String,
    val eventSink: (AudioBarEvent.CancelablePlaybackEvent) -> Unit
  ) : AudioBarState()

  data class Error(
    val message: String,
    val eventSink: (AudioBarEvent.CancelablePlaybackEvent) -> Unit
  ) : AudioBarState()

  data class Prompt(
    val message: String,
    val eventSink: (AudioBarEvent.PromptEvent) -> Unit
  ) : AudioBarState()

  sealed class RecitationState(val isRecitationActive: Boolean) : AudioBarState() {
    abstract val eventSink: (AudioBarEvent.CommonRecordingEvent) -> Unit
  }

  data class RecitationListening(
    override val eventSink: (AudioBarEvent.CommonRecordingEvent) -> Unit,
    val listeningEventSink: (AudioBarEvent.RecitationListeningEvent) -> Unit
  ) : RecitationState(true)

  data class RecitationPlaying(
    override val eventSink: (AudioBarEvent.CommonRecordingEvent) -> Unit,
    val playingEventSink: (AudioBarEvent.RecitationPlayingEvent) -> Unit
  ) : RecitationState(false)

  data class RecitationStopped(
    override val eventSink: (AudioBarEvent.CommonRecordingEvent) -> Unit,
    val stoppedEventSink: (AudioBarEvent.RecitationStoppedEvent) -> Unit
  ) : RecitationState(false)
}

sealed class AudioBarEvent : CircuitUiEvent {
  sealed class CommonPlaybackEvent : AudioBarEvent() {
    data object Stop : CommonPlaybackEvent()
    data object Rewind : CommonPlaybackEvent()
    data object FastForward : CommonPlaybackEvent()
    data class SetSpeed(val speed: Float) : CommonPlaybackEvent()
    data class SetRepeat(val repeat: Int) : CommonPlaybackEvent()
  }

  sealed class PlayingPlaybackEvent : AudioBarEvent() {
    data object Pause : PlayingPlaybackEvent()
  }

  sealed class PausedPlaybackEvent : AudioBarEvent() {
    data object Play : PausedPlaybackEvent()
  }

  sealed class CancelablePlaybackEvent : AudioBarEvent() {
    data object Cancel : CancelablePlaybackEvent()
  }

  sealed class StoppedPlaybackEvent : AudioBarEvent() {
    data object ChangeQari : StoppedPlaybackEvent()
    data object Play : StoppedPlaybackEvent()
    data object Record : StoppedPlaybackEvent()
  }

  sealed class PromptEvent : AudioBarEvent() {
    data object Cancel : PromptEvent()
    data object Acknowledge : PromptEvent()
  }

  sealed class CommonRecordingEvent : AudioBarEvent() {
    data object Recitation : CommonRecordingEvent()
    data object RecitationLongPress : CommonRecordingEvent()
    data object Transcript : CommonRecordingEvent()
  }

  sealed class RecitationListeningEvent : AudioBarEvent() {
    data object HideVerses : RecitationListeningEvent()
  }

  sealed class RecitationPlayingEvent : AudioBarEvent() {
    data object EndSession : RecitationPlayingEvent()
    data object PauseRecitation : RecitationPlayingEvent()
  }

  sealed class RecitationStoppedEvent : AudioBarEvent() {
    data object EndSession : RecitationStoppedEvent()
    data object PlayRecitation : RecitationStoppedEvent()
  }
}
