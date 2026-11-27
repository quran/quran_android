package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme

internal fun <T> repeatableButtonStateDescription(
  value: T,
  defaultValue: T,
  format: (T) -> String
): String? = if (value != defaultValue) format(value) else null

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
  val formattedValue = repeatableButtonStateDescription(value, defaultValue, format)
  Box(contentAlignment = Alignment.Center, modifier = modifier) {
    IconButton(
      onClick = {
        val index = (values.indexOf(value) + 1) % values.size
        onValueChanged(values[index])
      },
      modifier = if (formattedValue != null) {
        Modifier.semantics { stateDescription = formattedValue }
      } else {
        Modifier
      }
    ) {
      Icon(icon, contentDescription = contentDescription)
    }

    if (formattedValue != null) {
      Text(
        text = formattedValue,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(end = 8.dp)
          .clearAndSetSemantics {}
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
      contentDescription = "Repeat",
      values = listOf(0, 1, 2, 3, -1),
      value = 1,
      defaultValue = 0,
      format = { it.toString() },
      onValueChanged = {}
    )
  }
}
