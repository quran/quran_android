package com.quran.mobile.feature.qarilist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.qarilist.R

@Composable
fun QariRow(
  qariItem: QariItem,
  isSelected: Boolean,
  onItemSelected: ((QariItem) -> Unit),
  modifier: Modifier = Modifier
) {
  Row(
    modifier
      .fillMaxWidth()
      .clickable { onItemSelected(qariItem) }
      .padding(horizontal = 16.dp, vertical = 16.dp)
  ) {
    Text(
      text = qariItem.name,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.align(Alignment.CenterVertically).weight(1f)
    )

    if (isSelected) {
      Icon(
        imageVector = QuranIcons.Check,
        contentDescription = stringResource(R.string.qarilist_selected),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }
  }
}
