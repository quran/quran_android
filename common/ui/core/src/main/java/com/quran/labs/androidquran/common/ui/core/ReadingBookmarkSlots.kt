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
  private val teal = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.TEAL,
    colorResourceId = R.color.reading_bookmark_teal,
    nameResourceId = R.string.reading_bookmark_teal
  )

  private val orange = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.ORANGE,
    colorResourceId = R.color.reading_bookmark_orange,
    nameResourceId = R.string.reading_bookmark_orange
  )

  private val red = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.RED,
    colorResourceId = R.color.reading_bookmark_red,
    nameResourceId = R.string.reading_bookmark_red
  )

  operator fun get(slot: ReadingBookmarkType): ReadingBookmarkSlotSpec {
    return when (slot) {
      ReadingBookmarkType.TEAL -> teal
      ReadingBookmarkType.ORANGE -> orange
      ReadingBookmarkType.RED -> red
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
