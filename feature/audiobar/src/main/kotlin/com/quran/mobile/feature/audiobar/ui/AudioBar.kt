package com.quran.mobile.feature.audiobar.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.AudioBarState

@Composable
fun AudioBar(audioBarState: AudioBarState) {
  val modifier = Modifier
    .requiredWidthIn(max = 360.dp)
    .fillMaxWidth()
    .height(56.dp)

  when (audioBarState) {
    is AudioBarState.Paused -> PausedAudioBar(state = audioBarState, modifier = modifier)
    is AudioBarState.Playing -> PlayingAudioBar(state = audioBarState, modifier = modifier)
    is AudioBarState.Error -> ErrorAudioBar(state = audioBarState, modifier = modifier)
    is AudioBarState.Loading -> LoadingAudioBar(state = audioBarState, modifier = modifier)
    is AudioBarState.Prompt -> PromptingAudioBar(state = audioBarState, modifier = modifier)
    is AudioBarState.RecitationListening -> RecitationListeningAudioBar(
      state = audioBarState,
      modifier = modifier
    )

    is AudioBarState.RecitationPlaying -> RecitationPlayingAudioBar(
      state = audioBarState,
      modifier = modifier
    )

    is AudioBarState.RecitationStopped -> RecitationStoppedAudioBar(
      state = audioBarState,
      modifier = modifier
    )

    is AudioBarState.Stopped -> StoppedAudioBar(state = audioBarState, modifier = modifier)
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AudioBarStoppedPreview() {
  QuranTheme {
    Surface {
      AudioBar(audioBarState = AudioBarState.Stopped(
        qariName = "Abdul Basit",
        enableRecording = false,
        eventSink = {}
      ))
    }
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AudioBarPlayingPreview() {
  QuranTheme {
    Surface {
      AudioBar(audioBarState = AudioBarState.Playing(
        repeat = 1,
        speed = 1.5f,
        eventSink = {},
        playbackEventSink = {}
      ))
    }
  }
}
