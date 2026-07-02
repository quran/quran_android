package com.quran.mobile.feature.ayahbookmark.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState
import kotlinx.collections.immutable.persistentListOf

/**
 * The whole ayah save/edit surface, driven entirely by [state]. Renders either the
 * live-editing sheet or the "Bookmark removed" undo toast — never both. The caller
 * (typically a bottom sheet host) is responsible for the surrounding scrim/animation
 * and must give this a bounded height, since the collections list scrolls internally
 * while the header and footer stay pinned.
 */
@Composable
fun AyahBookmark(
  state: AyahBookmarkState,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxWidth()) {
    if (state.isBookmarkRemoved) {
      BookmarkRemovedToast(
        onUndo = { state.eventSink(AyahBookmarkEvent.UndoRemoveBookmark) },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(14.dp)
      )
    } else {
      AyahBookmarkSheet(state = state)
    }
  }
}

private val previewSuraAyahNameResolver: (Context, SuraAyah) -> String = { _, suraAyah ->
  "An-Nisāʾ ${suraAyah.ayah}"
}

private val previewCollections = persistentListOf(
  AyahBookmarkCollectionItem(id = "family", name = "Family", countLabel = 12, isChecked = true),
  AyahBookmarkCollectionItem(id = "favorites", name = "Favorites", countLabel = 34, isChecked = false),
  AyahBookmarkCollectionItem(id = "friday-reminders", name = "Friday reminders", countLabel = 3, isChecked = false),
  AyahBookmarkCollectionItem(id = "tarawih-planning", name = "Tarawih planning", countLabel = 8, isChecked = true),
  AyahBookmarkCollectionItem(id = "ramadan-goals", name = "Ramadan goals", countLabel = 21, isChecked = false),
  AyahBookmarkCollectionItem(id = "memorization", name = "Memorization", countLabel = 45, isChecked = false)
)

@Composable
private fun PreviewScaffold(state: AyahBookmarkState) {
  QuranTheme {
    Surface {
      Box(modifier = Modifier.height(560.dp)) {
        AyahBookmark(state = state)
      }
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("arabic", locale = "ar")
@Composable
private fun AyahBookmarkDefaultPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = true,
      currentReadingBookmark = SuraAyah(4, 34),
      collections = previewCollections,
      suraAyahNameResolver = previewSuraAyahNameResolver
    )
  )
}

@Preview("with last-place warning")
@Composable
private fun AyahBookmarkWarningPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = false,
      currentReadingBookmark = SuraAyah(4, 34),
      collections = previewCollections.replacingAt(0, previewCollections[0].copy(isChecked = false)),
      showLastPlaceWarning = true,
      suraAyahNameResolver = previewSuraAyahNameResolver
    )
  )
}

@Preview("creating a collection")
@Composable
private fun AyahBookmarkCreatingCollectionPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = true,
      collections = previewCollections,
      collectionCreation = AyahBookmarkCollectionCreationState.Active(name = "Qiyam"),
      suraAyahNameResolver = previewSuraAyahNameResolver
    )
  )
}

@Preview("creating a collection (submitting)")
@Composable
private fun AyahBookmarkCreatingCollectionSubmittingPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = true,
      collections = previewCollections,
      collectionCreation = AyahBookmarkCollectionCreationState.Active(name = "Qiyam", isSubmitting = true),
      suraAyahNameResolver = previewSuraAyahNameResolver
    )
  )
}

@Preview("bookmark removed / undo toast")
@Composable
private fun AyahBookmarkRemovedPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = false,
      collections = previewCollections,
      isBookmarkRemoved = true,
      suraAyahNameResolver = previewSuraAyahNameResolver
    )
  )
}
