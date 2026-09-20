package com.quran.mobile.feature.ayahbookmark.readingbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetEvent
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetState

@Composable
internal fun ReadingBookmarkSheet(
  state: ReadingBookmarkSheetState,
  modifier: Modifier = Modifier
) {
  val eventSink = state.eventSink
  val context = LocalContext.current
  val targetName = remember(context, state.target) {
    state.targetNameResolver(context, state.target)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .padding(bottom = 8.dp)
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 18.dp)
        .size(width = 36.dp, height = 4.dp)
        .clip(RoundedCornerShape(percent = 100))
        .background(MaterialTheme.colorScheme.outlineVariant)
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 14.dp, start = if (state.isNested) 6.dp else 18.dp, end = 18.dp)
    ) {
      if (state.isNested) {
        Icon(
          imageVector = QuranIcons.ArrowBack,
          contentDescription = stringResource(R.string.readingbookmark_back),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .clip(RoundedCornerShape(percent = 100))
            .clickable { eventSink(ReadingBookmarkSheetEvent.Dismiss) }
            .padding(11.dp)
            .size(22.dp)
        )
      }
      Text(
        text = stringResource(R.string.readingbookmark_title),
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.15.sp
        ),
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    Text(
      text = if (state.isNested) {
        stringResource(R.string.readingbookmark_placing_on, targetName)
      } else {
        stringResource(R.string.readingbookmark_subtitle)
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(
        top = 6.dp,
        bottom = 12.dp,
        start = if (state.isNested) 56.dp else 18.dp,
        end = 18.dp
      )
    )

    state.slots.forEachIndexed { index, item ->
      if (index > 0) {
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.padding(horizontal = 18.dp)
        )
      }
      ReadingBookmarkSlotRow(
        item = item,
        target = state.target,
        locationNameResolver = state.locationNameResolver,
        onPlace = { eventSink(ReadingBookmarkSheetEvent.PlaceSlot(item.slot)) },
        onClear = { eventSink(ReadingBookmarkSheetEvent.ClearSlot(item.slot)) }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}
