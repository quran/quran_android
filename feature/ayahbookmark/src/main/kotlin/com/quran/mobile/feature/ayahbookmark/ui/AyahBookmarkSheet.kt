package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState

@Composable
internal fun AyahBookmarkSheet(
  state: AyahBookmarkState,
  modifier: Modifier = Modifier
) {
  val eventSink = state.eventSink

  val context = LocalContext.current
  val suraAyahName = remember(state.ayah) {
    state.suraAyahNameResolver(context, state.ayah)
  }
  val currentReadingBookmarkName = remember(state.currentReadingBookmark) {
    state.currentReadingBookmark?.let { state.suraAyahNameResolver(context, it) }
  }

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
      .background(MaterialTheme.colorScheme.surface)
  ) {
    // fixed head: drag handle, title row, reading bookmark toggle, collections label
    Column(modifier = Modifier.padding(top = 18.dp, start = 18.dp, end = 18.dp)) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .padding(bottom = 14.dp)
          .size(width = 36.dp, height = 4.dp)
          .clip(RoundedCornerShape(percent = 100))
          .background(MaterialTheme.colorScheme.outlineVariant)
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 14.dp)
      ) {
        Text(
          text = suraAyahName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(R.string.ayahbookmark_done),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.End,
          modifier = Modifier
            .weight(1f)
            .clickable { eventSink(AyahBookmarkEvent.Done) }
            .padding(4.dp)
        )
      }

      ReadingBookmarkRow(
        isEnabled = state.isReadingBookmarkEnabled,
        currentReadingBookmarkName = currentReadingBookmarkName,
        onToggle = { eventSink(AyahBookmarkEvent.ToggleReadingBookmark) }
      )

      Text(
        text = stringResource(R.string.ayahbookmark_collections_header).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
      )
    }

    // scrollable body: the list of collections
    LazyColumn(
      modifier = Modifier
        .weight(1f, fill = false)
        .padding(horizontal = 18.dp)
    ) {
      items(state.collections, key = { it.id }) { collection ->
        CollectionRow(
          collection = collection,
          onToggle = { id -> eventSink(AyahBookmarkEvent.ToggleCollection(id)) }
        )
      }
    }

    // pinned footer: last-place warning, new collection, remove bookmark
    Column {
      if (state.showLastPlaceWarning) {
        LastPlaceWarningBanner(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
      }

      when (val creation = state.collectionCreation) {
        is AyahBookmarkCollectionCreationState.Inactive -> NewCollectionTriggerRow(
          onClick = { eventSink(AyahBookmarkEvent.StartCreatingCollection) }
        )

        is AyahBookmarkCollectionCreationState.Active -> NewCollectionInputRow(
          name = creation.name,
          isSubmitting = creation.isSubmitting,
          onNameChange = { eventSink(AyahBookmarkEvent.CollectionNameChanged(it)) },
          onCancel = { eventSink(AyahBookmarkEvent.CancelCreatingCollection) },
          onCreate = { eventSink(AyahBookmarkEvent.CreateCollection) }
        )
      }

      RemoveBookmarkRow(onClick = { eventSink(AyahBookmarkEvent.RemoveBookmark) })
    }
  }
}
