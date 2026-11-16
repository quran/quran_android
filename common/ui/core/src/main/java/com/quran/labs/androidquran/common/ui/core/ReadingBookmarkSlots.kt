package com.quran.labs.androidquran.common.ui.core

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.common.ui.core.R

@Immutable
data class ReadingBookmarkSlotSpec(
  val slot: ReadingBookmarkType,
  @ColorRes val colorResourceId: Int,
  @StringRes val nameResourceId: Int
)

object ReadingBookmarkSlots {
  private val specs = ReadingBookmarkType.entries.associateWith { slot ->
    when (slot) {
      ReadingBookmarkType.TEAL -> ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_teal, R.string.reading_bookmark_teal)
      ReadingBookmarkType.ORANGE ->
        ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_orange, R.string.reading_bookmark_orange)
      ReadingBookmarkType.RED -> ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_red, R.string.reading_bookmark_red)
      ReadingBookmarkType.GREEN ->
        ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_green, R.string.reading_bookmark_green)
      ReadingBookmarkType.BLUE -> ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_blue, R.string.reading_bookmark_blue)
      ReadingBookmarkType.PURPLE ->
        ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_purple, R.string.reading_bookmark_purple)
      ReadingBookmarkType.BROWN ->
        ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_brown, R.string.reading_bookmark_brown)
      ReadingBookmarkType.GREY -> ReadingBookmarkSlotSpec(slot, R.color.reading_bookmark_grey, R.string.reading_bookmark_grey)
    }
  }

  operator fun get(slot: ReadingBookmarkType): ReadingBookmarkSlotSpec = specs.getValue(slot)

  fun displayName(context: Context, slot: ReadingBookmarkType, name: String?): String =
    name?.takeIf { it.isNotBlank() } ?: context.getString(this[slot].nameResourceId)

  fun displayName(context: Context, bookmark: ReadingBookmark): String =
    displayName(context, bookmark.slot, bookmark.name)

  @Composable
  fun displayName(slot: ReadingBookmarkType, name: String?): String =
    name?.takeIf { it.isNotBlank() } ?: stringResource(this[slot].nameResourceId)
}
