package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.mobile.feature.ayahbookmark.R

@Composable
internal fun NewCollectionTriggerRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 22.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    PlusGlyph(color = MaterialTheme.colorScheme.primary)
    Text(
      text = stringResource(R.string.ayahbookmark_new_collection),
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
internal fun NewCollectionInputRow(
  name: String,
  isSubmitting: Boolean,
  onNameChange: (String) -> Unit,
  onCancel: () -> Unit,
  onCreate: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  val underlineColor = MaterialTheme.colorScheme.primary
  val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    PlusGlyph(color = MaterialTheme.colorScheme.primary)

    Box(modifier = Modifier.weight(1f)) {
      BasicTextField(
        value = name,
        onValueChange = onNameChange,
        enabled = !isSubmitting,
        singleLine = true,
        textStyle = TextStyle(
          fontSize = 14.5.sp,
          color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCreate() }),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .drawBehind {
            drawLine(
              color = underlineColor,
              start = Offset(0f, size.height),
              end = Offset(size.width, size.height),
              strokeWidth = strokeWidth
            )
          }
          .padding(vertical = 5.dp)
      )
      if (name.isEmpty()) {
        Text(
          text = stringResource(R.string.ayahbookmark_collection_name_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 5.dp)
        )
      }
    }

    Text(
      text = stringResource(R.string.ayahbookmark_cancel),
      style = MaterialTheme.typography.bodySmall,
      color = if (isSubmitting) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier
        .clickable(enabled = !isSubmitting) { onCancel() }
        .padding(6.dp)
    )

    TextButton(onClick = onCreate, enabled = !isSubmitting) {
      if (isSubmitting) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary
        )
      } else {
        Text(stringResource(R.string.ayahbookmark_create))
      }
    }
  }
}

@Composable
private fun PlusGlyph(color: Color, modifier: Modifier = Modifier) {
  Text(
    text = "+",
    fontSize = 20.sp,
    color = color,
    textAlign = TextAlign.Center,
    modifier = modifier.width(21.dp)
  )
}
