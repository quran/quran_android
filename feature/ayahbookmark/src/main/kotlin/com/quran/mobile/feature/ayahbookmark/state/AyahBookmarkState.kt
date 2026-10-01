package com.quran.mobile.feature.ayahbookmark.state

import android.content.Context
import androidx.compose.runtime.Immutable
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AyahBookmarkState(
  val ayah: SuraAyah,
  val suggestedReadingBookmark: ReadingBookmark,
  val isSuggestedReadingBookmarkEnabled: Boolean,
  val otherReadingBookmarks: ImmutableList<ReadingBookmark> = persistentListOf(),
  val currentAyahReadingBookmarks: List<ReadingBookmark>,
  val readingBookmarkSelection: ReadingBookmarkSheetState? = null,
  val collections: ImmutableList<AyahBookmarkCollectionItem> = persistentListOf(),
  val collectionCreation: AyahBookmarkCollectionCreationState = AyahBookmarkCollectionCreationState.Inactive,
  val highlight: Highlight?,
  val isDismissed: Boolean = false,
  val suraAyahNameResolver: (Context, SuraAyah) -> String,
  val readingBookmarkLocationResolver: (Context, ReadingBookmark) -> String,
  val eventSink: (AyahBookmarkEvent) -> Unit = {}
) {
  val isSaved: Boolean
    get() = currentAyahReadingBookmarks.isNotEmpty() || highlight != null || collections.any { it.isChecked }
}

@Immutable
data class AyahBookmarkCollectionItem(
  val id: String,
  val name: String,
  val isDefault: Boolean = false,
  val countLabel: Int,
  val isChecked: Boolean
)

@Immutable
sealed interface AyahBookmarkCollectionCreationState {
  data object Inactive : AyahBookmarkCollectionCreationState

  data class Active(
    val name: String,
    val isSubmitting: Boolean = false,
    val hasNameError: Boolean = false
  ) : AyahBookmarkCollectionCreationState
}

sealed interface AyahBookmarkEvent {
  data object PlaceSuggestedReadingBookmark : AyahBookmarkEvent
  data object ClearSuggestedReadingBookmark : AyahBookmarkEvent
  data object ShowReadingBookmarks : AyahBookmarkEvent
  data class ToggleCollection(val id: String) : AyahBookmarkEvent
  data object StartCreatingCollection : AyahBookmarkEvent
  data object CancelCreatingCollection : AyahBookmarkEvent
  data class CollectionNameChanged(val name: String) : AyahBookmarkEvent
  data class CreateCollection(val name: String) : AyahBookmarkEvent
  data class SetHighlight(val color: HighlightColor) : AyahBookmarkEvent
  data object ClearHighlight : AyahBookmarkEvent
  data object Done : AyahBookmarkEvent
}
