package com.quran.mobile.feature.ayahbookmark.readingbookmark.state

import android.content.Context
import androidx.compose.runtime.Immutable
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ReadingBookmarkSheetState(
  val target: ReadingBookmarkTarget,
  val slots: ImmutableList<ReadingBookmarkSlotItem>,
  val isNested: Boolean = false,
  val isDismissed: Boolean = false,
  val targetNameResolver: (Context, ReadingBookmarkTarget) -> String,
  val locationNameResolver: (Context, ReadingBookmark) -> String,
  val eventSink: (ReadingBookmarkSheetEvent) -> Unit = {}
)

@Immutable
data class ReadingBookmarkSlotItem(
  val slot: ReadingBookmarkType,
  val bookmark: ReadingBookmark?,
  val isAtTarget: Boolean
)

sealed interface ReadingBookmarkSheetEvent {
  data class PlaceSlot(val slot: ReadingBookmarkType) : ReadingBookmarkSheetEvent
  data class ClearSlot(val slot: ReadingBookmarkType) : ReadingBookmarkSheetEvent
  data object Dismiss : ReadingBookmarkSheetEvent
}
