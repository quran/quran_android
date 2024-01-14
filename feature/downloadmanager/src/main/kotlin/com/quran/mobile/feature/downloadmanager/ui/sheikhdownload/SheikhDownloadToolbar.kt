package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.ui.common.DownloadManagerToolbar

@Composable
fun SheikhDownloadToolbar(
  titleResource: Int,
  isContextual: Boolean,
  downloadIcon: Boolean,
  removeIcon: Boolean,
  downloadAction: (() -> Unit),
  eraseAction: (() -> Unit),
  onBackAction: (() -> Unit)
) {
  val actions: @Composable() (RowScope.() -> Unit) = {
    if (downloadIcon) {
      IconButton(onClick = downloadAction) {
        val contentDescription = if (isContextual) R.string.audio_manager_download_selection else R.string.audio_manager_download_all
        Icon(
          painterResource(id = R.drawable.ic_download),
          contentDescription = stringResource(id = contentDescription)
        )
      }
    }

    if (removeIcon) {
      IconButton(onClick = eraseAction) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = stringResource(id = R.string.audio_manager_delete_selection)
        )
      }
    }
  }

  DownloadManagerToolbar(
    title = if (isContextual) "" else stringResource(titleResource),
    onBackPressed = onBackAction,
    actions = actions
  )
}
