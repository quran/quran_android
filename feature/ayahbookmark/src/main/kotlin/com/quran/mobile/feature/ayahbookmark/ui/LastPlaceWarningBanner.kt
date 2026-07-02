package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.WarningIcon

@Composable
internal fun LastPlaceWarningBanner(modifier: Modifier = Modifier) {
  val colors = ayahBookmarkWarningColors
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(colors.background)
      .border(1.dp, colors.border, RoundedCornerShape(12.dp))
      .padding(horizontal = 13.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = WarningIcon,
      contentDescription = null,
      tint = colors.content,
      modifier = Modifier.size(17.dp)
    )
    Text(
      text = stringResource(R.string.ayahbookmark_last_place_warning),
      style = MaterialTheme.typography.bodySmall,
      color = colors.content,
      modifier = Modifier.padding(start = 10.dp)
    )
  }
}
