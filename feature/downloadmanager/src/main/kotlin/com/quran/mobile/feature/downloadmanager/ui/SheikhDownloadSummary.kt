package com.quran.mobile.feature.downloadmanager.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.downloadmanager.R
import com.quran.mobile.feature.downloadmanager.model.DownloadedSheikhUiModel
import com.quran.mobile.feature.downloadmanager.ui.common.DownloadCommonRow

@Composable
fun SheikhDownloadSummary(
  downloadedSheikhUiModel: DownloadedSheikhUiModel,
  onQariItemClicked: ((QariItem) -> Unit)
) {
  val (color, tintColor) = if (downloadedSheikhUiModel.downloadedSuras > 0) {
    Color(0xff5e8900) to MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
  }

  DownloadCommonRow(
    title = downloadedSheikhUiModel.qariItem.name,
    subtitle = pluralStringResource(
      R.plurals.audio_manager_files_downloaded,
      downloadedSheikhUiModel.downloadedSuras, downloadedSheikhUiModel.downloadedSuras
    ),
    onRowPressed = { onQariItemClicked(downloadedSheikhUiModel.qariItem) }
  ) {
    Image(
      painterResource(id = R.drawable.ic_download),
      contentDescription = "",
      colorFilter = ColorFilter.tint(tintColor),
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .clip(CircleShape)
        .background(color)
        .padding(8.dp)
    )
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SheikhDownloadSummaryPreview() {
  val qari = Qari(
    id = 1,
    nameResource = com.quran.labs.androidquran.common.audio.R.string.qari_minshawi_murattal_gapless,
    url = "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
    path = "minshawi_murattal",
    hasGaplessAlternative = false,
    db = "minshawi_murattal"
  )

  val downloadedSheikhModel = DownloadedSheikhUiModel(
    QariItem.fromQari(LocalContext.current, qari),
    downloadedSuras = 1
  )

  QuranTheme {
    Surface(color = MaterialTheme.colorScheme.surface) {
      SheikhDownloadSummary(downloadedSheikhUiModel = downloadedSheikhModel) { }
    }
  }
}
