package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.NoProgress
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
import kotlinx.coroutines.flow.Flow
import java.text.DecimalFormat

@Composable
fun DownloadProgressDialog(
  progressEvents: Flow<SuraDownloadStatusEvent.Progress>,
  onCancel: () -> Unit
) {
  val progressState = progressEvents.collectAsState(NoProgress)
  val decimalFormat = remember { DecimalFormat("###.00") }

  val currentEvent = progressState.value
  AlertDialog(
    title = {
      Text(
        text = stringResource(id = com.quran.mobile.common.download.R.string.downloading),
        modifier = Modifier.padding(top = 16.dp)
      )
    },
    text = {
      Column(modifier = Modifier.heightIn(min = 56.dp)) {
        Text(
          currentEvent.asMessage(LocalContext.current, decimalFormat),
          modifier = Modifier.padding(bottom = 8.dp, end = 16.dp)
        )
        if (currentEvent.progress == -1) {
          LinearProgressIndicator()
        } else {
          LinearProgressIndicator(
            progress = { (currentEvent.progress / 100.0f) },
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onCancel) {
        Text(text = stringResource(id = com.quran.mobile.common.ui.core.R.string.cancel))
      }
    },
    onDismissRequest = onCancel
  )
}

private fun SuraDownloadStatusEvent.Progress.asMessage(
  context: Context,
  decimalFormat: DecimalFormat,
): String {
  val megabyte = 1024 * 1024
  val downloaded = context.getString(
    R.string.download_amount_in_megabytes,
    decimalFormat.format(1.0 * downloadedAmount / megabyte)
  )

  val total = context.getString(
    R.string.download_amount_in_megabytes,
    decimalFormat.format(1.0 * totalAmount / megabyte)
  )

  return if (sura < 1) {
    // no sura, no ayah
    context.getString(R.string.download_progress, downloaded, total)
  } else if (ayah <= 0) {
    // sura, no ayah
    context.getString(R.string.download_sura_progress, downloaded, total, sura)
  } else {
    // sura and ayah
    context.getString(R.string.download_sura_ayah_progress, sura, ayah)
  }
}
