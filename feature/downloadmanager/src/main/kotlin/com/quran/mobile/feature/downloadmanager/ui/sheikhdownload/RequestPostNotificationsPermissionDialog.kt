package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quran.mobile.feature.downloadmanager.R

@Composable
fun RequestPostNotificationsPermissionDialog(
  onConfirmation: (() -> Unit),
  onDismiss: (() -> Unit),
) {
  AlertDialog(
    title = {
      Text(text = stringResource(id = R.string.audio_manager_post_notifications_permission_title))
    },
    text = {
      Text(text = stringResource(id = R.string.audio_manager_post_notifications_permission_description))
    },
    confirmButton = {
      TextButton(onClick = onConfirmation) {
        Text(text = stringResource(id = android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = com.quran.mobile.common.ui.core.R.string.cancel))
      }
    },
    onDismissRequest = onDismiss
  )
}
