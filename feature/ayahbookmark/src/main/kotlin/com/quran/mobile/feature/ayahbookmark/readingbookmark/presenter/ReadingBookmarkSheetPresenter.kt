package com.quran.mobile.feature.ayahbookmark.readingbookmark.presenter

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.isAt
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ReadingBookmarkAction
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetEvent
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSheetState
import com.quran.mobile.feature.ayahbookmark.readingbookmark.state.ReadingBookmarkSlotItem
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList

class ReadingBookmarkSheetPresenter @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranNaming: QuranNaming,
  private val quranInfo: QuranInfo
) {

  @Composable
  internal fun present(
    target: ReadingBookmarkTarget,
    isNested: Boolean,
    onAction: (ReadingBookmarkAction) -> Unit
  ): ReadingBookmarkSheetState {
    val readingBookmarks = readingBookmarksDao.readingBookmarksFlow().collectAsState(null)
    val slots = remember(readingBookmarks.value, target) {
      val placed = readingBookmarks.value
        .orEmpty()
        .filterNot { it is EmptyReadingBookmark }
        .associateBy { it.slot }
      ReadingBookmarkType.entries.map { slot ->
        val bookmark = placed[slot]
        ReadingBookmarkSlotItem(
          slot = slot,
          bookmark = bookmark,
          isAtTarget = bookmark?.isAt(target) == true
        )
      }.toImmutableList()
    }

    val isDismissed = remember { mutableStateOf(false) }

    val eventSink: (ReadingBookmarkSheetEvent) -> Unit = { event ->
      isDismissed.value = true
      when (event) {
        is ReadingBookmarkSheetEvent.PlaceSlot ->
          onAction(ReadingBookmarkAction.Place(event.slot, target))

        is ReadingBookmarkSheetEvent.ClearSlot ->
          onAction(ReadingBookmarkAction.Clear(event.slot))

        ReadingBookmarkSheetEvent.Dismiss -> {}
      }
    }

    return ReadingBookmarkSheetState(
      target = target,
      slots = slots,
      isNested = isNested,
      isDismissed = isDismissed.value,
      targetNameResolver = { context, bookmarkTarget ->
        when (bookmarkTarget) {
          is ReadingBookmarkTarget.Page -> quranNaming.getSuraPageString(context, bookmarkTarget.page)
          is ReadingBookmarkTarget.Ayah -> {
            val suraAyah = bookmarkTarget.suraAyah
            quranNaming.getSuraAyahString(context, suraAyah.sura, suraAyah.ayah)
          }
        }
      },
      locationNameResolver = { context, bookmark -> locationName(context, bookmark) },
      eventSink = eventSink
    )
  }

  private fun locationName(context: Context, bookmark: ReadingBookmark): String {
    return when (bookmark) {
      is PageReadingBookmark -> quranNaming.getSuraPageString(context, bookmark.page)
      is AyahReadingBookmark -> context.getString(
        R.string.readingbookmark_ayah_location,
        quranNaming.getSuraAyahString(context, bookmark.sura, bookmark.ayah),
        quranInfo.getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
      )
      is EmptyReadingBookmark -> ""
    }
  }
}
