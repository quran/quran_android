package com.quran.mobile.feature.ayahbookmark.ui

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetEvent
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ui.ReadingBookmarkSheet
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun AyahBookmark(
  state: AyahBookmarkState,
  modifier: Modifier = Modifier
) {
  val selection = state.readingBookmarkSelection

  BackHandler(enabled = selection != null) {
    selection?.eventSink(ReadingBookmarkSheetEvent.Dismiss)
  }

  AnimatedContent(
    targetState = selection,
    contentKey = { it != null },
    transitionSpec = {
      val towardsList = targetState != null
      val direction = if (towardsList) SlideDirection.Start else SlideDirection.End
      (slideIntoContainer(direction) + fadeIn()) togetherWith
        (slideOutOfContainer(direction) + fadeOut())
    },
    label = "readingBookmarkSelection",
    modifier = modifier.fillMaxWidth()
  ) { target ->
    if (target == null) {
      AyahBookmarkSheet(state = state)
    } else {
      ReadingBookmarkSheet(
        state = target,
        containerColor = MaterialTheme.colorScheme.surface
      )
    }
  }
}

private val previewAyah = SuraAyah(4, 6)

private val previewSuraAyahNameResolver: (Context, SuraAyah) -> String = { _, suraAyah ->
  "An-Nisāʾ ${suraAyah.sura}:${suraAyah.ayah}"
}

private val previewLocationResolver: (Context, ReadingBookmark) -> String = { _, bookmark ->
  when (bookmark) {
    is PageReadingBookmark -> "An-Nisāʾ (Page ${bookmark.page})"
    is AyahReadingBookmark -> "An-Nisāʾ ${bookmark.sura}:${bookmark.ayah} · Page 77"
    is EmptyReadingBookmark -> ""
  }
}

private fun previewOtherSlots(suggested: ReadingBookmarkType): ImmutableList<ReadingBookmark> =
  ReadingBookmarkType.entries
    .filterNot { it == suggested }
    .mapIndexed { index, slot ->
      if (index == 0) {
        PageReadingBookmark(slot, page = 120, timestamp = Instant.DISTANT_PAST)
      } else {
        EmptyReadingBookmark(slot, timestamp = Instant.DISTANT_PAST)
      }
    }
    .toImmutableList()

private val previewCollections = persistentListOf(
  AyahBookmarkCollectionItem(id = "family", name = "Family", countLabel = 12, isChecked = true),
  AyahBookmarkCollectionItem(
    id = "favorites",
    name = "Favorites",
    isDefault = true,
    countLabel = 34,
    isChecked = false
  ),
  AyahBookmarkCollectionItem(
    id = "friday-reminders",
    name = "Friday reminders",
    countLabel = 3,
    isChecked = false
  ),
  AyahBookmarkCollectionItem(
    id = "tarawih-planning",
    name = "Tarawih planning",
    countLabel = 8,
    isChecked = true
  ),
  AyahBookmarkCollectionItem(
    id = "ramadan-goals",
    name = "Ramadan goals",
    countLabel = 21,
    isChecked = false
  ),
  AyahBookmarkCollectionItem(
    id = "memorization",
    name = "Memorization",
    countLabel = 45,
    isChecked = false
  )
)

private val previewUncheckedCollections =
  previewCollections.map { it.copy(isChecked = false) }.toImmutableList()

private fun previewHighlight(color: HighlightColor) =
  Highlight(previewAyah, color, Instant.fromEpochMilliseconds(0))

private fun previewState(
  suggested: ReadingBookmark,
  collections: ImmutableList<AyahBookmarkCollectionItem> = previewCollections,
  collectionCreation: AyahBookmarkCollectionCreationState = AyahBookmarkCollectionCreationState.Inactive,
  highlight: Highlight? = previewHighlight(HighlightColor.YELLOW)
): AyahBookmarkState {
  val isAtAyah = suggested is AyahReadingBookmark && suggested.asSuraAyah() == previewAyah
  return AyahBookmarkState(
    ayah = previewAyah,
    suggestedReadingBookmark = suggested,
    isSuggestedReadingBookmarkEnabled = isAtAyah,
    otherReadingBookmarks = previewOtherSlots(suggested.slot),
    currentAyahReadingBookmarks = if (isAtAyah) listOf(suggested) else emptyList(),
    collections = collections,
    collectionCreation = collectionCreation,
    highlight = highlight,
    suraAyahNameResolver = previewSuraAyahNameResolver,
    readingBookmarkLocationResolver = previewLocationResolver
  )
}

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

@Preview("pin on this ayah")
@Preview("pin on this ayah (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("pin on this ayah (arabic)", locale = "ar")
@Composable
private fun AyahBookmarkPinOnThisAyahPreview() {
  PreviewScaffold(
    state = previewState(
      suggested = AyahReadingBookmark(
        slot = ReadingBookmarkType.CORAL,
        sura = previewAyah.sura,
        ayah = previewAyah.ayah,
        timestamp = Clock.System.now()
      )
    )
  )
}

@Preview("pin elsewhere")
@Preview("pin elsewhere (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AyahBookmarkPinElsewherePreview() {
  PreviewScaffold(
    state = previewState(
      suggested = PageReadingBookmark(
        slot = ReadingBookmarkType.CORAL,
        page = 75,
        timestamp = Clock.System.now()
      ),
      highlight = null
    )
  )
}

@Preview("nothing saved")
@Preview("nothing saved (dark theme)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AyahBookmarkNothingSavedPreview() {
  PreviewScaffold(
    state = previewState(
      suggested = EmptyReadingBookmark(ReadingBookmarkType.TEAL, Clock.System.now()),
      collections = previewUncheckedCollections,
      highlight = null
    )
  )
}

@Preview("highlight only")
@Composable
private fun AyahBookmarkHighlightOnlyPreview() {
  PreviewScaffold(
    state = previewState(
      suggested = PageReadingBookmark(
        slot = ReadingBookmarkType.INDIGO,
        page = 83,
        timestamp = Clock.System.now()
      ),
      collections = previewUncheckedCollections,
      highlight = previewHighlight(HighlightColor.PURPLE)
    )
  )
}

@Preview("creating a collection")
@Composable
private fun AyahBookmarkCreatingCollectionPreview() {
  PreviewScaffold(
    state = previewState(
      suggested = EmptyReadingBookmark(ReadingBookmarkType.INDIGO, Clock.System.now()),
      collectionCreation = AyahBookmarkCollectionCreationState.Active(name = "Qiyam"),
      highlight = previewHighlight(HighlightColor.GREEN)
    )
  )
}

@Preview("creating a collection (submitting)")
@Composable
private fun AyahBookmarkCreatingCollectionSubmittingPreview() {
  PreviewScaffold(
    state = previewState(
      suggested = EmptyReadingBookmark(ReadingBookmarkType.INDIGO, Clock.System.now()),
      collectionCreation = AyahBookmarkCollectionCreationState.Active(
        name = "Qiyam",
        isSubmitting = true
      ),
      highlight = previewHighlight(HighlightColor.BLUE)
    )
  )
}
