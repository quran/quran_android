package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarState
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvent

@Composable
internal fun PlayingAudioBar(
  state: AudioBarState.Playing,
  eventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit,
  playbackEventSink: (AudioBarUiEvent.PlayingPlaybackEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  AudioBar(state, eventSink, modifier) {
    IconButton(
      modifier = Modifier
        .width(40.dp)
        .weight(1f),
      onClick = { playbackEventSink(AudioBarUiEvent.PlayingPlaybackEvent.Pause) }) {
      Icon(QuranIcons.Pause, contentDescription = "")
    }
  }
}

@Composable
internal fun PausedAudioBar(
  state: AudioBarState.Paused,
  eventSink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit,
  pausedEventSink: (AudioBarUiEvent.PausedPlaybackEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  AudioBar(state = state, eventSink, modifier = modifier) {
    IconButton(
      modifier = Modifier
        .width(40.dp)
        .weight(1f),
      onClick = { pausedEventSink(AudioBarUiEvent.PausedPlaybackEvent.Play) }) {
      Icon(QuranIcons.PlayArrow, contentDescription = "")
    }
  }
}

@Composable
internal fun AudioBar(
  state: AudioBarState.ActivePlayback,
  sink: (AudioBarUiEvent.CommonPlaybackEvent) -> Unit,
  modifier: Modifier = Modifier,
  actionButton: @Composable RowScope.() -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier
    ) {
      IconButton(
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        onClick = { sink(AudioBarUiEvent.CommonPlaybackEvent.Stop) }) {
        Icon(QuranIcons.Stop, contentDescription = "", modifier = Modifier.width(40.dp))
      }

      IconButton(
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        onClick = { sink(AudioBarUiEvent.CommonPlaybackEvent.Rewind) }) {
        Icon(QuranIcons.FastRewind, contentDescription = "", modifier = Modifier.width(40.dp))
      }

      actionButton()

      IconButton(
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        onClick = { sink(AudioBarUiEvent.CommonPlaybackEvent.FastForward) }) {
        Icon(QuranIcons.FastForward, contentDescription = "", modifier = Modifier.width(40.dp))
      }

      val infinity = stringResource(id = com.quran.mobile.common.ui.core.R.string.infinity)
      RepeatableButton(
        icon = QuranIcons.Repeat,
        contentDescription = "",
        values = REPEAT_VALUES,
        value = state.repeat,
        defaultValue = 0,
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        format = {
          if (it > -1) {
            it.toString()
          } else {
            infinity
          }
        }
      ) {
        sink(AudioBarUiEvent.CommonPlaybackEvent.SetRepeat(it))
      }

      RepeatableButton(
        icon = QuranIcons.Speed,
        contentDescription = "",
        values = SPEED_VALUES,
        value = state.speed,
        defaultValue = 1.0f,
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        format = { it.toString() }
      ) {
        sink(AudioBarUiEvent.CommonPlaybackEvent.SetSpeed(it))
      }

      IconButton(
        modifier = Modifier
          .width(40.dp)
          .weight(1f),
        onClick = { sink(AudioBarUiEvent.CommonPlaybackEvent.ShowSettings) }) {
        Icon(QuranIcons.Settings, contentDescription = "")
      }
    }
  }
}

private val REPEAT_VALUES = listOf(0, 1, 2, 3, -1)
private val SPEED_VALUES = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

@Preview
@Composable
private fun PlayingAudioBarPreview() {
  QuranTheme {
    PlayingAudioBar(
      state = AudioBarState.Playing(
        repeat = 0,
        speed = 1.0f
      ),
      eventSink = {},
      playbackEventSink = {}
    )
  }
}

@Preview
@Composable
private fun PausedAudioBarPreview() {
  QuranTheme {
    PausedAudioBar(
      state = AudioBarState.Paused(
        repeat = 1,
        speed = 0.5f
      ),
      eventSink = {},
      pausedEventSink = {}
    )
  }
}
