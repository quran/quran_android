package com.quran.mobile.feature.ayahbookmark.readingbookmark.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSlotItem
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkIcon
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkOutlineIcon

@Composable
internal fun ReadingBookmarkSlotRow(
  item: ReadingBookmarkSlotItem,
  target: ReadingBookmarkTarget,
  isEditing: Boolean,
  locationNameResolver: (Context, ReadingBookmark) -> String,
  onPlace: () -> Unit,
  onClear: () -> Unit,
  onNameChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val spec = remember(item.slot) { ReadingBookmarkSlots[item.slot] }
  val pinColor = colorResource(spec.colorResourceId)
  val context = LocalContext.current
  val locationName = remember(context, item.bookmark) {
    item.bookmark?.let { locationNameResolver(context, it) }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp, vertical = if (isEditing) 12.dp else 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (item.bookmark == null) BookmarkOutlineIcon else BookmarkIcon,
      contentDescription = null,
      tint = pinColor,
      modifier = Modifier.size(22.dp)
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 14.dp)
    ) {
      if (isEditing) {
        ReadingBookmarkNameField(item = item, onNameChange = onNameChange)
      } else {
        Text(
          text = ReadingBookmarkSlots.displayName(item.slot, item.name),
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Text(
        text = when {
          item.isAtTarget -> stringResource(
            when (target) {
              is ReadingBookmarkTarget.Page -> R.string.readingbookmark_on_this_page
              is ReadingBookmarkTarget.Ayah -> R.string.readingbookmark_on_this_ayah
            }
          )
          locationName != null -> stringResource(R.string.readingbookmark_at_location, locationName)
          else -> stringResource(R.string.readingbookmark_not_placed)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = if (isEditing) 4.dp else 0.dp)
      )
    }

    if (!isEditing) {
      Spacer(modifier = Modifier.width(10.dp))

      if (item.isAtTarget) {
        OutlinedButton(
          onClick = onClear,
          shape = RoundedCornerShape(percent = 100),
          contentPadding = ButtonContentPadding
        ) {
          Text(
            text = stringResource(R.string.readingbookmark_clear),
            style = MaterialTheme.typography.labelLarge
          )
        }
      } else {
        Button(
          onClick = onPlace,
          shape = RoundedCornerShape(percent = 100),
          contentPadding = ButtonContentPadding,
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          Text(
            text = if (item.bookmark == null) {
              stringResource(R.string.readingbookmark_set_here)
            } else {
              stringResource(R.string.readingbookmark_move_here)
            },
            style = MaterialTheme.typography.labelLarge
          )
        }
      }
    }
  }
}

@Composable
private fun ReadingBookmarkNameField(
  item: ReadingBookmarkSlotItem,
  onNameChange: (String) -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val strokeWidth = with(LocalDensity.current) { 1.dp.toPx() }
  val underlineColor = MaterialTheme.colorScheme.outlineVariant

  val textFieldValue = remember(item.slot) {
    mutableStateOf(
      TextFieldValue(text = item.draftName, selection = TextRange(item.draftName.length))
    )
  }

  Box {
    BasicTextField(
      value = textFieldValue.value,
      onValueChange = { newValue ->
        textFieldValue.value = newValue
        onNameChange(newValue.text)
      },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
      ),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawLine(
            color = underlineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth
          )
        }
        .padding(bottom = 5.dp)
    )

    if (textFieldValue.value.text.isEmpty()) {
      Text(
        text = ReadingBookmarkSlots.displayName(item.slot, null),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 5.dp)
      )
    }
  }
}

private val ButtonContentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
