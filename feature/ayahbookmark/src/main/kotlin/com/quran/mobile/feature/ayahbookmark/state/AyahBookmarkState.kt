package com.quran.mobile.feature.ayahbookmark.state

import android.content.Context
import androidx.compose.runtime.Immutable
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AyahBookmarkState(
  val ayah: SuraAyah,
  val isReadingBookmarkEnabled: Boolean,
  val currentReadingBookmark: ReadingBookmark? = null,
  val collections: ImmutableList<AyahBookmarkCollectionItem> = persistentListOf(),
  val collectionCreation: AyahBookmarkCollectionCreationState = AyahBookmarkCollectionCreationState.Inactive,
  val highlight: Highlight?,
  val showLastPlaceWarning: Boolean = false,
  val showRemoveBookmarkButton: Boolean = true,
  val isBookmarkRemoved: Boolean = false,
  val isDismissed: Boolean = false,
  val suraAyahNameResolver: (Context, SuraAyah) -> String,
  val readingBookmarkNameResolver: (Context, ReadingBookmark) -> String,
  val eventSink: (AyahBookmarkEvent) -> Unit = {}
)

@Immutable
data class AyahBookmarkCollectionItem(
  val id: String,
  val name: String,
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
  data object ToggleReadingBookmark : AyahBookmarkEvent
  data class ToggleCollection(val id: String) : AyahBookmarkEvent
  data object StartCreatingCollection : AyahBookmarkEvent
  data object CancelCreatingCollection : AyahBookmarkEvent
  data class CollectionNameChanged(val name: String) : AyahBookmarkEvent
  data class CreateCollection(val name: String) : AyahBookmarkEvent
  data class SetHighlight(val color: HighlightColor) : AyahBookmarkEvent
  data object ClearHighlight : AyahBookmarkEvent
  data object RemoveBookmark : AyahBookmarkEvent
  data object UndoRemoveBookmark : AyahBookmarkEvent
  data object Done : AyahBookmarkEvent
}
