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
  private val green = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.GREEN,
    colorResourceId = R.color.reading_bookmark_green,
    nameResourceId = R.string.reading_bookmark_green
  )

  private val purple = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.PURPLE,
    colorResourceId = R.color.reading_bookmark_purple,
    nameResourceId = R.string.reading_bookmark_purple
  )

  private val blue = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.BLUE,
    colorResourceId = R.color.reading_bookmark_blue,
    nameResourceId = R.string.reading_bookmark_blue
  )

  operator fun get(slot: ReadingBookmarkType): ReadingBookmarkSlotSpec {
    return when (slot) {
      ReadingBookmarkType.GREEN -> green
      ReadingBookmarkType.PURPLE -> purple
      ReadingBookmarkType.BLUE -> blue
    }
  }

  fun displayName(context: Context, slot: ReadingBookmarkType, name: String?): String =
    name?.takeIf { it.isNotBlank() } ?: context.getString(this[slot].nameResourceId)

  fun displayName(context: Context, bookmark: ReadingBookmark): String =
    displayName(context, bookmark.slot, bookmark.name)

  @Composable
  fun displayName(slot: ReadingBookmarkType, name: String?): String =
    name?.takeIf { it.isNotBlank() } ?: stringResource(this[slot].nameResourceId)
}
