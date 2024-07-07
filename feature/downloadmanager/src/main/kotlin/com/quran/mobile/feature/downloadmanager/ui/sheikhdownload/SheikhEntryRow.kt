package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.ui.common.DownloadCommonRow

@Composable
fun SheikhEntryRow(
  sheikhEntryUiModel: EntryForQari,
  isSelected: Boolean,
  suraNaming: (Context, Int) -> String,
  databaseNaming: (Context) -> String,
  @StringRes downloadEntryResourceId: Int,
  @StringRes deleteEntryResourceId: Int,
  onEntryClicked: ((EntryForQari) -> Unit),
  onEntryLongClicked: ((EntryForQari) -> Unit)
) {
  val modifier = if (isSelected) {
    Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)
  } else { Modifier }

  val title = when (sheikhEntryUiModel) {
    is EntryForQari.SuraForQari -> suraNaming(LocalContext.current, sheikhEntryUiModel.sura)
    is EntryForQari.DatabaseForQari -> databaseNaming(LocalContext.current)
  }

  if (sheikhEntryUiModel.isDownloaded) {
    DownloadCommonRow(
      title = title,
      subtitle = stringResource(id = deleteEntryResourceId),
      modifier = modifier,
      onRowPressed = { onEntryClicked(sheikhEntryUiModel) },
      onRowLongPressed = { onEntryLongClicked(sheikhEntryUiModel) }
    ) {
      Image(
        imageVector = Icons.Filled.Close,
        contentDescription = stringResource(deleteEntryResourceId),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onError),
        modifier = Modifier
          .align(Alignment.CenterVertically)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.error)
          .padding(8.dp)
      )
    }
  } else {
    DownloadCommonRow(
      title = title,
      subtitle = stringResource(id = downloadEntryResourceId),
      modifier = modifier,
      onRowPressed = { onEntryClicked(sheikhEntryUiModel) },
      onRowLongPressed = { onEntryLongClicked(sheikhEntryUiModel) }
    ) {
      Image(
        painterResource(id = R.drawable.ic_download),
        contentDescription = stringResource(downloadEntryResourceId),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiary),
        modifier = Modifier
          .align(Alignment.CenterVertically)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.tertiary)
          .padding(8.dp)
      )
    }
  }
}

