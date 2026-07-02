package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.RemoveBookmarkIcon

@Composable
internal fun RemoveBookmarkRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dividerColor = MaterialTheme.colorScheme.outlineVariant
  val dividerWidth = with(LocalDensity.current) { 1.dp.toPx() }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .drawBehind {
        drawLine(
          color = dividerColor,
          start = Offset.Zero,
          end = Offset(size.width, 0f),
          strokeWidth = dividerWidth
        )
      }
      .clickable { onClick() }
      .padding(13.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = RemoveBookmarkIcon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(15.dp)
    )
    Text(
      text = stringResource(R.string.ayahbookmark_remove_bookmark),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}
