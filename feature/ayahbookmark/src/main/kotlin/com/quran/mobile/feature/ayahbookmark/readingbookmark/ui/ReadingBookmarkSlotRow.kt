package com.quran.mobile.feature.ayahbookmark.readingbookmark.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSlotItem
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkIcon
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkOutlineIcon

/**
 * One reading bookmark slot: its pin, where that pin currently sits, and the single tap that either
 * brings it here or takes it off here.
 */
@Composable
internal fun ReadingBookmarkSlotRow(
  item: ReadingBookmarkSlotItem,
  target: ReadingBookmarkTarget,
  locationNameResolver: (Context, ReadingBookmark) -> String,
  onPlace: () -> Unit,
  onClear: () -> Unit,
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
      .padding(horizontal = 18.dp, vertical = 14.dp),
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
      Text(
        text = stringResource(spec.nameResourceId),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface
      )
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
        // tinting "on this ayah" with the pin's own color is what makes the row that is already
        // here read differently from the two that aren't, at a glance
        color = if (item.isAtTarget) pinColor else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    if (item.isAtTarget) {
      // a removal shouldn't be the most eager thing on screen, so it is outlined rather than filled
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

private val ButtonContentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
