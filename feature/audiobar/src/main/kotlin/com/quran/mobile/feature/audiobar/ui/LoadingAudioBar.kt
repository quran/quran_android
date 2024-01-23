package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.AudioBarEvent
import com.quran.mobile.feature.audiobar.AudioBarState

@Composable
fun LoadingAudioBar(state: AudioBarState.Loading, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.height(IntrinsicSize.Min)
  ) {
    IconButton(onClick = { sink(AudioBarEvent.CancelablePlaybackEvent.Cancel) }) {
      Icon(QuranIcons.Close, contentDescription = stringResource(id = android.R.string.cancel))
    }

    Divider(modifier = Modifier
      .fillMaxHeight()
      .width(Dp.Hairline))

    Column {
      if (state.progress == -1) {
        LinearProgressIndicator()
      } else {
        LinearProgressIndicator(progress = state.progress.toFloat() / 100f)
      }

      Text(text = state.message)
    }
  }
}

@Preview
@Composable
fun LoadingAudioBarPreview() {
  QuranTheme {
    LoadingAudioBar(
      state = AudioBarState.Loading(
        progress = 50,
        message = "Downloading...",
        eventSink = {}
      )
    )
  }
}

@Preview
@Composable
fun LoadingAudioBarIndeterminatePreview() {
  QuranTheme {
    LoadingAudioBar(
      state = AudioBarState.Loading(
        progress = -1,
        message = "Loading...",
        eventSink = {}
      )
    )
  }
}
