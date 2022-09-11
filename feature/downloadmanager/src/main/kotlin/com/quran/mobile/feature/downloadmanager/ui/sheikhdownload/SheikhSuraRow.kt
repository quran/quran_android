package com.quran.mobile.feature.downloadmanager.ui.sheikhdownload

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
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import com.quran.mobile.feature.downloadmanager.ui.common.DownloadCommonRow
import com.quran.page.common.data.QuranNaming

@Composable
fun SheikhSuraRow(
  sheikhSuraUiModel: SuraForQari,
  isSelected: Boolean,
  quranNaming: QuranNaming,
  onSuraClicked: ((SuraForQari) -> Unit),
  onSuraLongClicked: ((SuraForQari) -> Unit)
) {
  val modifier = if (isSelected) {
    Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)
  } else { Modifier }

  if (sheikhSuraUiModel.isDownloaded) {
    DownloadCommonRow(
      title = quranNaming.getSuraName(LocalContext.current, sheikhSuraUiModel.sura),
      subtitle = stringResource(id = R.string.audio_manager_surah_delete),
      modifier = modifier,
      onRowPressed = { onSuraClicked(sheikhSuraUiModel) },
      onRowLongPressed = { onSuraLongClicked(sheikhSuraUiModel) }
    ) {
      Image(
        imageVector = Icons.Filled.Close,
        contentDescription = stringResource(R.string.audio_manager_surah_delete),
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
      title = quranNaming.getSuraName(LocalContext.current, sheikhSuraUiModel.sura),
      subtitle = stringResource(id = R.string.audio_manager_surah_download),
      modifier = modifier,
      onRowPressed = { onSuraClicked(sheikhSuraUiModel) },
      onRowLongPressed = { onSuraLongClicked(sheikhSuraUiModel) }
    ) {
      Image(
        painterResource(id = R.drawable.ic_download),
        contentDescription = stringResource(R.string.audio_manager_surah_download),
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
