package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarScreen

@Composable
fun PlayingAudioBar(state: AudioBarScreen.AudioBarState.Playing, modifier: Modifier = Modifier) {
  val sink = state.playbackEventSink

  AudioBar(state, modifier) {
    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.PlayingPlaybackEvent.Pause) }) {
      Icon(QuranIcons.Pause, contentDescription = "")
    }
  }
}

@Composable
fun PausedAudioBar(state: AudioBarScreen.AudioBarState.Paused, modifier: Modifier = Modifier) {
  val sink = state.pausedEventSink

  AudioBar(state = state, modifier = modifier) {
    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.PausedPlaybackEvent.Play) }) {
      Icon(QuranIcons.PlayArrow, contentDescription = "")
    }
  }
}

@Composable
fun AudioBar(
  state: AudioBarScreen.AudioBarState.ActivePlayback,
  modifier: Modifier = Modifier,
  actionButton: @Composable () -> Unit
) {
  val sink = state.eventSink
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.Stop) }) {
      Icon(QuranIcons.Stop, contentDescription = "")
    }

    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.Rewind) }) {
      Icon(QuranIcons.FastRewind, contentDescription = "")
    }

    actionButton()

    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.FastForward) }) {
      Icon(QuranIcons.FastForward, contentDescription = "")
    }

    RepeatableButton(
      icon = QuranIcons.Repeat,
      contentDescription = "",
      values = REPEAT_VALUES,
      value = state.repeat,
      defaultValue = 0,
      format = { it.toString() }
    ) {
      sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.SetRepeat(it))
    }

    RepeatableButton(
      icon = QuranIcons.Speed,
      contentDescription = "",
      values = SPEED_VALUES,
      value = state.speed,
      defaultValue = 1.0f,
      format = { it.toString() }
    ) {
      sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.SetSpeed(it))
    }

    IconButton(onClick = { sink(AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.ShowSettings) }) {
      Icon(QuranIcons.Settings, contentDescription = "")
    }
  }
}

private val REPEAT_VALUES = listOf(0, 1, 2, 3, -1)
private val SPEED_VALUES = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

@Preview
@Composable
fun PlayingAudioBarPreview() {
  QuranTheme {
    PlayingAudioBar(
      state = AudioBarScreen.AudioBarState.Playing(
        repeat = 0,
        speed = 1.0f,
        eventSink = {},
        playbackEventSink = {}
      )
    )
  }
}

@Preview
@Composable
fun PausedAudioBarPreview() {
  QuranTheme {
    PausedAudioBar(
      state = AudioBarScreen.AudioBarState.Paused(
        repeat = 1,
        speed = 0.5f,
        eventSink = {},
        pausedEventSink = {}
      )
    )
  }
}
