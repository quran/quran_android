package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.RemovedIcon

@Composable
internal fun BookmarkRemovedToast(
  onUndo: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(13.dp))
      .background(MaterialTheme.colorScheme.inverseSurface)
      .padding(horizontal = 15.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = RemovedIcon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
      modifier = Modifier.size(16.dp)
    )
    Text(
      text = stringResource(R.string.ayahbookmark_bookmark_removed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.inverseOnSurface,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 12.dp)
    )
    Text(
      text = stringResource(R.string.ayahbookmark_undo),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.inversePrimary,
      modifier = Modifier
        .clickable { onUndo() }
        .padding(2.dp)
    )
  }
}
