package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarState
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvent

@Composable
internal fun PromptingAudioBar(
  state: AudioBarState.Prompt,
  eventSink: (AudioBarUiEvent.PromptEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.height(IntrinsicSize.Min)
  ) {
    IconButton(onClick = { eventSink(AudioBarUiEvent.PromptEvent.Acknowledge) }) {
      Icon(QuranIcons.Close, contentDescription = stringResource(id = android.R.string.ok))
    }

    Divider(
      modifier = Modifier
        .fillMaxHeight()
        .width(Dp.Hairline)
    )

    Text(text = stringResource(state.messageResource))
    Spacer(modifier = Modifier.weight(1f))

    IconButton(onClick = { eventSink(AudioBarUiEvent.PromptEvent.Cancel) }) {
      Icon(QuranIcons.Close, contentDescription = stringResource(id = android.R.string.cancel))
    }
  }
}

@Preview
@Composable
fun PromptingAudioBarPreview() {
  QuranTheme {
    PromptingAudioBar(
      state = AudioBarState.Prompt(
        messageResource = android.R.string.httpErrorUnsupportedScheme,
      ),
      eventSink = {}
    )
  }
}
