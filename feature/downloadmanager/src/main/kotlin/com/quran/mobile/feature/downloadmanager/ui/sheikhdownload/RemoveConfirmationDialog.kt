package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.common.ui.core.R as commonR

@Composable
fun RemoveConfirmationDialog(
  onConfirmation: (() -> Unit),
  onDismiss: (() -> Unit),
  title: String? = null
) {
  AlertDialog(
    title = {
      Text(text = stringResource(id = R.string.audio_manager_remove_audio_title))
    },
    text = {
      if (title != null) {
        Text(text = stringResource(id = R.string.audio_manager_remove_audio_msg, title))
      } else {
        Text(text = stringResource(id = R.string.audio_manager_remove_multiple_audio_msg))
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirmation) {
        Text(text = stringResource(id = commonR.string.remove_button))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = commonR.string.cancel))
      }
    },
    onDismissRequest = onDismiss
  )
}
