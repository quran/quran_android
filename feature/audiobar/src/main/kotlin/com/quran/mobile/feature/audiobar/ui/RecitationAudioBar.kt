package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvent
import com.quran.mobile.feature.audiobar.state.AudioBarState

@Composable
fun RecitationListeningAudioBar(
  state: AudioBarState.RecitationListening,
  modifier: Modifier = Modifier
) {
  val sink = state.listeningEventSink
  RecitationAudioBar(state = state, modifier = modifier) {
    IconButton(onClick = { sink(AudioBarUiEvent.RecitationListeningEvent.HideVerses) }) {
      Icon(QuranIcons.MenuBook, contentDescription = "")
    }
  }
}

@Composable
fun RecitationPlayingAudioBar(
  state: AudioBarState.RecitationPlaying,
  modifier: Modifier = Modifier
) {
  val sink = state.playingEventSink
  RecitationAudioBar(state = state, modifier = modifier) {
    IconButton(onClick = { sink(AudioBarUiEvent.RecitationPlayingEvent.EndSession) }) {
      Icon(QuranIcons.Close, contentDescription = "")
    }

    IconButton(onClick = { sink(AudioBarUiEvent.RecitationPlayingEvent.PauseRecitation) }) {
      Icon(QuranIcons.Pause, contentDescription = "")
    }
  }
}

@Composable
fun RecitationStoppedAudioBar(
  state: AudioBarState.RecitationStopped,
  modifier: Modifier = Modifier
) {
  val sink = state.stoppedEventSink
  RecitationAudioBar(state = state, modifier = modifier) {
    IconButton(onClick = { sink(AudioBarUiEvent.RecitationStoppedEvent.EndSession) }) {
      Icon(QuranIcons.Close, contentDescription = "")
    }

    IconButton(onClick = { sink(AudioBarUiEvent.RecitationStoppedEvent.PlayRecitation) }) {
      Icon(QuranIcons.PlayArrow, contentDescription = "")
    }
  }
}

@Composable
fun RecitationAudioBar(
  state: AudioBarState.RecitationState,
  modifier: Modifier = Modifier,
  actions: @Composable () -> Unit
) {
  val sink = state.eventSink
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.height(IntrinsicSize.Min)
  ) {
    actions()

    Divider(
      modifier = Modifier
        .fillMaxHeight()
        .width(Dp.Hairline)
    )

    Spacer(modifier = Modifier.weight(1f))

    Divider(
      modifier = Modifier
        .fillMaxHeight()
        .width(Dp.Hairline)
    )

    IconButton(onClick = { sink(AudioBarUiEvent.CommonRecordingEvent.Transcript) }) {
      Icon(QuranIcons.Chat, contentDescription = "")
    }

    Divider(
      modifier = Modifier
        .fillMaxHeight()
        .width(Dp.Hairline)
    )

    Box(
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(40.dp)
        .clip(CircleShape)
        .background(color = Color.Transparent)
        .combinedClickable(
          role = Role.Button,
          onClick = { sink(AudioBarUiEvent.CommonRecordingEvent.Recitation) },
          onLongClick = { sink(AudioBarUiEvent.CommonRecordingEvent.RecitationLongPress) },
        ),
      contentAlignment = Alignment.Center
    ) {
      val tint = if (state.isRecitationActive) {
        MaterialTheme.colorScheme.primary
      } else {
        LocalContentColor.current
      }
      Icon(QuranIcons.Mic, contentDescription = "", tint = tint)
    }
  }
}

@Preview
@Composable
fun RecitationListeningAudioBarPreview() {
  QuranTheme {
    RecitationListeningAudioBar(
      state = AudioBarState.RecitationListening(
        eventSink = {},
        listeningEventSink = {},
      )
    )
  }
}

@Preview
@Composable
fun RecitationPlayingAudioBarPreview() {
  QuranTheme {
    RecitationPlayingAudioBar(
      state = AudioBarState.RecitationPlaying(
        eventSink = {},
        playingEventSink = {},
      )
    )
  }
}

@Preview
@Composable
fun RecitationStoppedAudioBarPreview() {
  QuranTheme {
    RecitationStoppedAudioBar(
      state = AudioBarState.RecitationStopped(
        eventSink = {},
        stoppedEventSink = {},
      )
    )
  }
}
