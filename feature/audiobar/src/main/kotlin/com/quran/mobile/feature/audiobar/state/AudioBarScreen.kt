package com.quran.mobile.feature.audiobar.state

internal sealed class AudioBarState {
  sealed class ActivePlayback : AudioBarState() {
    abstract val repeat: Int
    abstract val speed: Float
  }

  data class Playing(
    override val repeat: Int,
    override val speed: Float
  ) : ActivePlayback()

  data class Paused(
    override val repeat: Int,
    override val speed: Float
  ) : ActivePlayback()

  data class Stopped(
    val qariNameResource: Int,
    val enableRecording: Boolean
  ) : AudioBarState()

  data class Downloading(
    val progress: Int,
    val messageResource: Int
  ) : AudioBarState()

  data class Loading(
    val progress: Int,
    val messageResource: Int
  ) : AudioBarState()

  data class Error(
    val messageResource: Int
  ) : AudioBarState()

  data class Prompt(
    val messageResource: Int
  ) : AudioBarState()

  data object RecitationListening : AudioBarState()

  data object RecitationPlaying : AudioBarState()

  data object RecitationStopped : AudioBarState()
}

internal class AudioBarUiEvents(
  val commonPlaybackEventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit = {},
  val playingPlaybackEventSink: (AudioBarUiEvent.PlayingPlaybackEvent) -> Unit = {},
  val pausedPlaybackEventSink: (AudioBarUiEvent.PausedPlaybackEvent) -> Unit = {},
  val stoppedEventSink: (AudioBarUiEvent.StoppedPlaybackEvent) -> Unit = {},
  val downloadingEventSink: (AudioBarUiEvent.DownloadingPlaybackEvent) -> Unit = {},
  val cancelableEventSink: (AudioBarUiEvent.CancelablePlaybackEvent) -> Unit = {},
  val promptEventSink: (AudioBarUiEvent.PromptEvent) -> Unit = {},
  val commonRecordingEventSink: (AudioBarUiEvent.CommonRecordingEvent) -> Unit = {},
  val recitationListeningEventSink: (AudioBarUiEvent.RecitationListeningEvent) -> Unit = {},
  val recitationPlayingEventSink: (AudioBarUiEvent.RecitationPlayingEvent) -> Unit = {},
  val recitationStoppedEventSink: (AudioBarUiEvent.RecitationStoppedEvent) -> Unit = {}
)

internal sealed class AudioBarUiEvent(val audioBarEvent: AudioBarEvent) {
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
    data object Play : PausedPlaybackEvent(AudioBarEvent.ResumePlayback)
  }

  sealed class DownloadingPlaybackEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
    data object Cancel : DownloadingPlaybackEvent(AudioBarEvent.CancelDownload)
  }

  sealed class CancelablePlaybackEvent(event: AudioBarEvent) : AudioBarUiEvent(event) {
    data object Cancel : CancelablePlaybackEvent(AudioBarEvent.Cancel)
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
