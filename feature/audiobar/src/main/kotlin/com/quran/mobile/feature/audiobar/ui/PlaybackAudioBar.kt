package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
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
import com.quran.mobile.feature.audiobar.AudioBarEvent
import com.quran.mobile.feature.audiobar.AudioBarState

@Composable
fun PlayingAudioBar(state: AudioBarState.Playing, modifier: Modifier = Modifier) {
  val sink = state.playbackEventSink

  AudioBar(state, modifier) {
    IconButton(onClick = { sink(AudioBarEvent.PlayingPlaybackEvent.Pause) }) {
      Icon(QuranIcons.Pause, contentDescription = "")
    }
  }
}

@Composable
fun PausedAudioBar(state: AudioBarState.Paused, modifier: Modifier = Modifier) {
  val sink = state.pausedEventSink

  AudioBar(state = state, modifier = modifier) {
    IconButton(onClick = { sink(AudioBarEvent.PausedPlaybackEvent.Play) }) {
      Icon(QuranIcons.PlayArrow, contentDescription = "")
    }
  }
}

@Composable
fun AudioBar(
  state: AudioBarState.ActivePlayback,
  modifier: Modifier = Modifier,
  actionButton: @Composable () -> Unit
) {
  val sink = state.eventSink
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    IconButton(onClick = { sink(AudioBarEvent.CommonPlaybackEvent.Stop) }) {
      Icon(QuranIcons.Stop, contentDescription = "")
    }

    IconButton(onClick = { sink(AudioBarEvent.CommonPlaybackEvent.Rewind) }) {
      Icon(QuranIcons.FastRewind, contentDescription = "")
    }

    actionButton()

    IconButton(onClick = { sink(AudioBarEvent.CommonPlaybackEvent.FastForward) }) {
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
      sink(AudioBarEvent.CommonPlaybackEvent.SetRepeat(it))
    }

    RepeatableButton(
      icon = QuranIcons.Speed,
      contentDescription = "",
      values = SPEED_VALUES,
      value = state.speed,
      defaultValue = 1.0f,
      format = { it.toString() }
    ) {
      sink(AudioBarEvent.CommonPlaybackEvent.SetSpeed(it))
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
      state = AudioBarState.Playing(
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
      state = AudioBarState.Paused(
        repeat = 1,
        speed = 0.5f,
        eventSink = {},
        pausedEventSink = {}
      )
    )
  }
}
