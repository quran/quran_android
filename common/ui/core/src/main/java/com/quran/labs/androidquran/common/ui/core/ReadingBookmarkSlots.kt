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
  private val coral = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.CORAL,
    colorResourceId = R.color.reading_bookmark_coral,
    nameResourceId = R.string.reading_bookmark_coral
  )

  private val teal = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.TEAL,
    colorResourceId = R.color.reading_bookmark_teal,
    nameResourceId = R.string.reading_bookmark_teal
  )

  private val indigo = ReadingBookmarkSlotSpec(
    slot = ReadingBookmarkType.INDIGO,
    colorResourceId = R.color.reading_bookmark_indigo,
    nameResourceId = R.string.reading_bookmark_indigo
  )

  operator fun get(slot: ReadingBookmarkType): ReadingBookmarkSlotSpec {
    return when (slot) {
      ReadingBookmarkType.CORAL -> coral
      ReadingBookmarkType.TEAL -> teal
      ReadingBookmarkType.INDIGO -> indigo
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
