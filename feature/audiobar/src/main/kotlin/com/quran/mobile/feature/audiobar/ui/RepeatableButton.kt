package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme

@Composable
fun <T> RepeatableButton(
  icon: ImageVector,
  contentDescription: String,
  values: List<T>,
  value: T,
  defaultValue: T,
  format: (T) -> String,
  modifier: Modifier = Modifier,
  onValueChanged: (T) -> Unit
) {
  Box(contentAlignment = Alignment.Center, modifier = modifier) {
    IconButton(onClick = {
      val index = (values.indexOf(value) + 1) % values.size
      onValueChanged(values[index])
    }) {
      Icon(icon, contentDescription = contentDescription)
    }

    if (value != defaultValue) {
      Text(
        text = format(value),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(end = 8.dp)
      )
    }
  }
}

@Preview
@Composable
private fun RepeatableButtonPreview() {
  QuranTheme {
    RepeatableButton(
      icon = QuranIcons.Repeat,
      contentDescription = "",
      values = listOf(0, 1, 2, 3, -1),
      value = 1,
      defaultValue = 0,
      format = { it.toString() },
      onValueChanged = {}
    )
  }
}
