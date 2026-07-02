package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem

@Composable
internal fun CollectionRow(
  collection: AyahBookmarkCollectionItem,
  onToggle: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .toggleable(
        value = collection.isChecked,
        role = Role.Checkbox,
        onValueChange = { onToggle(collection.id) }
      )
      .padding(horizontal = 4.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    CollectionCheckbox(isChecked = collection.isChecked)
    Text(
      text = collection.name,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 12.dp)
    )
    Text(
      text = collection.countLabel.toString(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun CollectionCheckbox(isChecked: Boolean, modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(21.dp)
      .clip(RoundedCornerShape(6.dp))
      .then(
        if (isChecked) {
          Modifier.background(MaterialTheme.colorScheme.primary)
        } else {
          Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
        }
      )
  ) {
    if (isChecked) {
      Icon(
        imageVector = QuranIcons.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(12.dp)
      )
    }
  }
}
