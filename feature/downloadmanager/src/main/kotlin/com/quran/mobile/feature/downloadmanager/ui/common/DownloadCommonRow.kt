package com.quran.mobile.feature.downloadmanager.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.audio.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme

@Composable
fun DownloadCommonRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onRowPressed: (() -> Unit),
  onRowLongPressed: (() -> Unit)? = null,
  icon: @Composable RowScope.() -> Unit
) {
  val baseModifier = modifier.fillMaxWidth()
  val clickModifier =
    if (onRowLongPressed != null) {
      baseModifier
        .combinedClickable(
          onClick = { onRowPressed() },
          onLongClick = { onRowLongPressed() }
        )
    } else {
      baseModifier
        .clickable { onRowPressed() }
    }

  Row(
    modifier = clickModifier
      .padding(16.dp)
  ) {
    icon()
    Column(
      modifier = Modifier
        .padding(start = 8.dp)
        .align(Alignment.CenterVertically)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Preview
@Preview("arabic", locale = "ar")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DownloadCommonRowPreview() {
  QuranTheme {
    Surface(color = MaterialTheme.colorScheme.surface) {
      DownloadCommonRow(
        title = stringResource(id = R.string.qari_minshawi_murattal_gapless),
        subtitle = stringResource(id = com.quran.mobile.feature.downloadmanager.R.string.audio_manager_surah_download),
        onRowPressed = { }
      ) {
        Image(
          imageVector = Icons.Filled.Clear,
          contentDescription = "Test Icon",
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
}

