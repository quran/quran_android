package com.quran.mobile.feature.ayahbookmark.state

import android.content.Context
import androidx.compose.runtime.Immutable
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AyahBookmarkState(
  val ayah: SuraAyah,
  // TODO: remove when applying multiple reading bookmarks
  val isReadingBookmarkEnabled: Boolean,
  // TODO: remove when applying multiple reading bookmarks
  val currentReadingBookmark: ReadingBookmark? = null,
  val isSuggestedReadingBookmarkEnabled: Boolean,
  val suggestedReadingBookmark: ReadingBookmark,
  val currentAyahReadingBookmarks: List<ReadingBookmark>,
  val collections: ImmutableList<AyahBookmarkCollectionItem> = persistentListOf(),
  val collectionCreation: AyahBookmarkCollectionCreationState = AyahBookmarkCollectionCreationState.Inactive,
  val highlight: Highlight?,
  val isDismissed: Boolean = false,
  val suraAyahNameResolver: (Context, SuraAyah) -> String,
  val suraPageNameResolver: (Context, Int) -> String,
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

  /** State for the inline collection editor, including persistence-level name rejection. */
  data class Active(
    val name: String,
    val isSubmitting: Boolean = false,
    val hasNameError: Boolean = false
  ) : AyahBookmarkCollectionCreationState
}

sealed interface AyahBookmarkEvent {
  data class ToggleReadingBookmark(val type: ReadingBookmarkType) : AyahBookmarkEvent
  data class ToggleCollection(val id: String) : AyahBookmarkEvent
  data object StartCreatingCollection : AyahBookmarkEvent
  data object CancelCreatingCollection : AyahBookmarkEvent
  data class CollectionNameChanged(val name: String) : AyahBookmarkEvent
  data class CreateCollection(val name: String) : AyahBookmarkEvent
  data class SetHighlight(val color: HighlightColor) : AyahBookmarkEvent
  data object ClearHighlight : AyahBookmarkEvent
  data object Done : AyahBookmarkEvent
}
