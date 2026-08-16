package com.quran.mobile.feature.ayahbookmark.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

@Composable
fun AyahBookmark(
  state: AyahBookmarkState,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxWidth()) {
    AyahBookmarkSheet(state = state)
  }
}

private val previewSuraAyahNameResolver: (Context, SuraAyah) -> String = { _, suraAyah ->
  "An-Nisāʾ ${suraAyah.ayah}"
}

private val previewReadingBookmarkNameResolver: (Context, ReadingBookmark) -> String = { _, bookmark ->
  when (bookmark) {
    is AyahReadingBookmark -> "An-Nisāʾ ${bookmark.ayah}"
    is PageReadingBookmark -> "An-Nisāʾ (Page ${bookmark.page})"
  }
}

private val previewCollections = persistentListOf(
  AyahBookmarkCollectionItem(id = "family", name = "Family", countLabel = 12, isChecked = true),
  AyahBookmarkCollectionItem(id = "favorites", name = "Favorites", countLabel = 34, isChecked = false),
  AyahBookmarkCollectionItem(id = "friday-reminders", name = "Friday reminders", countLabel = 3, isChecked = false),
  AyahBookmarkCollectionItem(id = "tarawih-planning", name = "Tarawih planning", countLabel = 8, isChecked = true),
  AyahBookmarkCollectionItem(id = "ramadan-goals", name = "Ramadan goals", countLabel = 21, isChecked = false),
  AyahBookmarkCollectionItem(id = "memorization", name = "Memorization", countLabel = 45, isChecked = false)
)

private val previewUncheckedCollections =
  previewCollections.map { it.copy(isChecked = false) }.toImmutableList()

private fun previewHighlight(color: HighlightColor) =
  Highlight(SuraAyah(4, 1), color, Instant.fromEpochMilliseconds(0))

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
      currentReadingBookmark = AyahReadingBookmark(sura = 4, ayah = 34, timestamp = 0),
      collections = previewCollections,
      highlight = previewHighlight(HighlightColor.YELLOW),
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
    )
  )
}

@Preview("no highlight")
@Preview("no highlight (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AyahBookmarkNoHighlightPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = true,
      currentReadingBookmark = AyahReadingBookmark(sura = 4, ayah = 34, timestamp = 0),
      collections = previewCollections,
      highlight = null,
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
    )
  )
}

@Preview("highlight only")
@Composable
private fun AyahBookmarkHighlightOnlyPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = false,
      currentReadingBookmark = PageReadingBookmark(page = 83, timestamp = 0),
      collections = previewUncheckedCollections,
      highlight = previewHighlight(HighlightColor.PURPLE),
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
    )
  )
}

@Preview("nothing saved")
@Preview("nothing saved (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AyahBookmarkNothingSavedPreview() {
  PreviewScaffold(
    state = AyahBookmarkState(
      ayah = SuraAyah(4, 1),
      isReadingBookmarkEnabled = false,
      currentReadingBookmark = PageReadingBookmark(page = 83, timestamp = 0),
      collections = previewUncheckedCollections,
      highlight = null,
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
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
      highlight = previewHighlight(HighlightColor.GREEN),
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
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
      highlight = previewHighlight(HighlightColor.BLUE),
      suraAyahNameResolver = previewSuraAyahNameResolver,
      readingBookmarkNameResolver = previewReadingBookmarkNameResolver
    )
  )
}
