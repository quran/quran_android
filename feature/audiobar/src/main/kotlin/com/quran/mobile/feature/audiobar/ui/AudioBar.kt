package com.quran.mobile.feature.audiobar.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarState
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvents
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun AudioBar(
  flow: StateFlow<AudioBarState>,
  eventListeners: AudioBarUiEvents,
  modifier: Modifier = Modifier
) {
  val state = flow.collectAsState()
  AudioBar(audioBarState = state.value, eventListeners, modifier = modifier)
}

@Composable
internal fun AudioBar(
  audioBarState: AudioBarState,
  eventListeners: AudioBarUiEvents,
  modifier: Modifier = Modifier
) {
  val updatedModifier = modifier

  when (audioBarState) {
    is AudioBarState.Paused -> PausedAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.commonPlaybackEventSink,
      pausedEventSink = eventListeners.pausedPlaybackEventSink
    )

    is AudioBarState.Playing -> PlayingAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.commonPlaybackEventSink,
      playbackEventSink = eventListeners.playingPlaybackEventSink
    )

    is AudioBarState.Error -> ErrorAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.cancelableEventSink
    )

    is AudioBarState.Loading -> LoadingAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.cancelableEventSink
    )

    is AudioBarState.Downloading -> DownloadingAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.downloadingEventSink
    )

    is AudioBarState.Prompt -> PromptingAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.promptEventSink
    )

    is AudioBarState.RecitationListening -> RecitationListeningAudioBar(
      eventSink = eventListeners.commonRecordingEventSink,
      listeningEventSink = eventListeners.recitationListeningEventSink,
      modifier = updatedModifier
    )

    is AudioBarState.RecitationPlaying -> RecitationPlayingAudioBar(
      eventSink = eventListeners.commonRecordingEventSink,
      playingEventSink = eventListeners.recitationPlayingEventSink,
      modifier = updatedModifier
    )

    is AudioBarState.RecitationStopped -> RecitationStoppedAudioBar(
      eventSink = eventListeners.commonRecordingEventSink,
      stoppedEventSink = eventListeners.recitationStoppedEventSink,
      modifier = updatedModifier
    )

    is AudioBarState.Stopped -> StoppedAudioBar(
      state = audioBarState,
      modifier = updatedModifier,
      eventSink = eventListeners.stoppedEventSink
    )
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AudioBarStoppedPreview() {
  QuranTheme {
    Surface {
      AudioBar(
        audioBarState = AudioBarState.Stopped(
          qariNameResource = com.quran.labs.androidquran.common.audio.R.string.qari_abdulbaset,
          enableRecording = false
        ),
        AudioBarUiEvents(),
        modifier = Modifier.height(48.dp),
      )
    }
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AudioBarPlayingPreview() {
  QuranTheme {
    Surface {
      AudioBar(
        AudioBarState.Playing(repeat = 1, speed = 1.5f),
        AudioBarUiEvents(),
        modifier = Modifier.height(48.dp)
      )
    }
  }
}
