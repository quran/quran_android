package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
      Icon(QuranIcons.Check, contentDescription = stringResource(id = android.R.string.ok))
    }

    VerticalDivider()

    Text(
      text = stringResource(state.messageResource),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .weight(1f)
    )

    IconButton(onClick = { eventSink(AudioBarUiEvent.PromptEvent.Cancel) }) {
      Icon(QuranIcons.Close, contentDescription = stringResource(id = android.R.string.cancel))
    }
  }
}

@Preview
@Composable
private fun PromptingAudioBarPreview() {
  QuranTheme {
    Surface {
      PromptingAudioBar(
        state = AudioBarState.Prompt(
          messageResource = com.quran.mobile.common.download.R.string.download_non_wifi_prompt,
        ),
        modifier = Modifier.height(48.dp),
        eventSink = {}
      )
    }
  }
}
