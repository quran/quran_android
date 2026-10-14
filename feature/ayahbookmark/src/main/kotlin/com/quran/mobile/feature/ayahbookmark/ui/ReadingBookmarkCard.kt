package com.quran.mobile.feature.ayahbookmark.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkIcon
import com.quran.mobile.feature.ayahbookmark.ui.icons.BookmarkOutlineIcon
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ReadingBookmarkCard(
  suggested: ReadingBookmark,
  isAtCurrentAyah: Boolean,
  others: ImmutableList<ReadingBookmark>,
  locationResolver: (Context, ReadingBookmark) -> String,
  onPlace: () -> Unit,
  onClear: () -> Unit,
  onShowOthers: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(14.dp)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
      .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape)
  ) {
    SuggestedReadingBookmarkRow(
      suggested = suggested,
      isAtCurrentAyah = isAtCurrentAyah,
      locationResolver = locationResolver,
      onPlace = onPlace,
      onClear = onClear
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))

    OtherReadingBookmarksRow(others = others, onClick = onShowOthers)
  }
}

@Composable
private fun SuggestedReadingBookmarkRow(
  suggested: ReadingBookmark,
  isAtCurrentAyah: Boolean,
  locationResolver: (Context, ReadingBookmark) -> String,
  onPlace: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier
) {
  val spec = remember(suggested.slot) { ReadingBookmarkSlots[suggested.slot] }
  val slotName = ReadingBookmarkSlots.displayName(suggested.slot, suggested.name)
  val isPlaced = suggested !is EmptyReadingBookmark

  Row(
    modifier = modifier.padding(horizontal = 14.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (isPlaced) BookmarkIcon else BookmarkOutlineIcon,
      contentDescription = null,
      tint = colorResource(spec.colorResourceId),
      modifier = Modifier.size(20.dp)
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 11.dp)
    ) {
      Text(
        text = when {
          isAtCurrentAyah -> stringResource(R.string.ayahbookmark_reading_bookmark_is_here, slotName)
          isPlaced -> stringResource(R.string.ayahbookmark_reading_bookmark_move_here, slotName)
          else -> stringResource(R.string.ayahbookmark_reading_bookmark_set_here, slotName)
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
      )

      // where the pin sits today - the title already says it when the pin is on this ayah
      if (!isAtCurrentAyah) {
        val context = LocalContext.current
        Text(
          text = if (isPlaced) {
            stringResource(
              R.string.readingbookmark_at_location,
              remember(context, suggested) { locationResolver(context, suggested) }
            )
          } else {
            stringResource(R.string.readingbookmark_not_placed)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    if (isAtCurrentAyah) {
      // a removal shouldn't be the most eager thing on screen, so it is outlined rather than filled
      OutlinedButton(
        onClick = onClear,
        shape = RoundedCornerShape(percent = 100),
        contentPadding = ButtonContentPadding,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.primary
        )
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
          text = if (isPlaced) {
            stringResource(R.string.ayahbookmark_reading_bookmark_move)
          } else {
            stringResource(R.string.ayahbookmark_reading_bookmark_set)
          },
          style = MaterialTheme.typography.labelLarge
        )
      }
    }
  }
}

@Composable
private fun OtherReadingBookmarksRow(
  others: ImmutableList<ReadingBookmark>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // the remaining pins, overlapping like a stack of cards, each one showing whether it is placed
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.width(others.stackWidth())
    ) {
      others.forEachIndexed { index, bookmark ->
        Icon(
          imageVector = if (bookmark is EmptyReadingBookmark) BookmarkOutlineIcon else BookmarkIcon,
          contentDescription = null,
          tint = colorResource(ReadingBookmarkSlots[bookmark.slot].colorResourceId),
          modifier = Modifier
            .offset(x = -GlyphOverlap * index)
            .size(GlyphSize)
        )
      }
    }

    Text(
      text = stringResource(R.string.ayahbookmark_other_reading_bookmark),
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .weight(1f)
        .padding(start = 11.dp)
    )

    Icon(
      imageVector = QuranIcons.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp)
    )
  }
}

private val ButtonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val GlyphSize = 14.dp
private val GlyphOverlap = 5.dp

private fun ImmutableList<ReadingBookmark>.stackWidth(): Dp =
  if (isEmpty()) 0.dp else GlyphSize * size - GlyphOverlap * (size - 1)
