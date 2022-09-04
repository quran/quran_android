package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.contentColorFor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraOption

@Composable
fun SuraRangeDialog(
  items: List<SuraOption>,
  onDownloadSelected: ((Int, Int) -> Unit),
  onDismiss: (() -> Unit)
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties()
  ) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val startSura = remember { mutableStateOf(1) }
    val endingSura = remember { mutableStateOf(114) }

    Surface(
      modifier = Modifier,
      shape = MaterialTheme.shapes.medium,
      color = backgroundColor,
      contentColor = contentColorFor(backgroundColor)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = stringResource(id = R.string.audio_manager_download_all),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(vertical = 16.dp)
        )
        AutoCompleteDropdown(
          stringResource(com.quran.mobile.common.ui.core.R.string.from),
          initialText = items.first().name,
          items
        ) { startSura.value = it }
        AutoCompleteDropdown(
          stringResource(com.quran.mobile.common.ui.core.R.string.to),
          initialText = items.last().name,
          items
        ) { endingSura.value = it }

        TextButton(
          modifier = Modifier.align(Alignment.End),
          onClick = { onDownloadSelected(startSura.value, endingSura.value) }
        ) {
          Text(
            text = stringResource(id = R.string.audio_manager_download_selection).uppercase(),
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }
  }
}
