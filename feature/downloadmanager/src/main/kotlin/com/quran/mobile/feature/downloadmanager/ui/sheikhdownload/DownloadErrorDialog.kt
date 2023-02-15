package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.material.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quran.mobile.feature.downloadmanager.R

@Composable
fun DownloadErrorDialog(errorMessage: String, onAcknowledgementListener: (() -> Unit)) {
  AlertDialog(
    title = {
      Text(text = stringResource(id = R.string.downloading_title))
    },
    text = {
      Text(text = errorMessage)
    },
    confirmButton = {
      TextButton(onClick = onAcknowledgementListener) {
        Text(text = stringResource(id = android.R.string.ok))
      }
    },
    onDismissRequest = onAcknowledgementListener
  )
}
