package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkIcon

@Composable
internal fun ReadingBookmarkRow(
  isEnabled: Boolean,
  currentReadingBookmarkName: String?,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
      .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
      .clickable { onToggle() }
      .padding(horizontal = 14.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = BookmarkIcon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp)
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 11.dp)
    ) {
      Text(
        text = stringResource(R.string.ayahbookmark_reading_bookmark_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      val subtitle = if (currentReadingBookmarkName == null) {
        stringResource(R.string.ayahbookmark_reading_bookmark_subtitle)
      } else if (isEnabled) {
        stringResource(R.string.ayahbookmark_reading_bookmark_will_move_from, currentReadingBookmarkName)
      } else {
        stringResource(R.string.ayahbookmark_reading_bookmark_currently_at, currentReadingBookmarkName)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Switch(
      checked = isEnabled,
      onCheckedChange = { onToggle() },
      colors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline
      )
    )
  }
}
