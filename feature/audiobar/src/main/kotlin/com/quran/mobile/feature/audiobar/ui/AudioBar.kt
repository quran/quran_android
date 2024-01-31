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
import com.quran.data.di.QuranReadingScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarScreen
import com.slack.circuit.codegen.annotations.CircuitInject

@CircuitInject(screen = AudioBarScreen::class, scope = QuranReadingScope::class)
@Composable
fun AudioBar(audioBarState: AudioBarScreen.AudioBarState, modifier: Modifier = Modifier) {
  val updatedModifier = modifier
    .requiredWidthIn(max = 360.dp)
    .fillMaxWidth()
    .height(56.dp)

  when (audioBarState) {
    is AudioBarScreen.AudioBarState.Paused -> PausedAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.Playing -> PlayingAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.Error -> ErrorAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.Loading -> LoadingAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.Prompt -> PromptingAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.RecitationListening -> RecitationListeningAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.RecitationPlaying -> RecitationPlayingAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.RecitationStopped -> RecitationStoppedAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )

    is AudioBarScreen.AudioBarState.Stopped -> StoppedAudioBar(
      state = audioBarState,
      modifier = updatedModifier
    )
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AudioBarStoppedPreview() {
  QuranTheme {
    Surface {
      AudioBar(audioBarState = AudioBarScreen.AudioBarState.Stopped(
        qariNameResource = com.quran.labs.androidquran.common.audio.R.string.qari_abdulbaset,
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
      AudioBar(audioBarState = AudioBarScreen.AudioBarState.Playing(
        repeat = 1,
        speed = 1.5f,
        eventSink = {},
        playbackEventSink = {}
      ))
    }
  }
}
