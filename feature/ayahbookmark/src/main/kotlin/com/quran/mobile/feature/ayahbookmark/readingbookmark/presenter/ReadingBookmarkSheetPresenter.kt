package com.quran.mobile.feature.ayahbookmark.readingbookmark.presenter

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
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
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AssistedInject
class ReadingBookmarkSheetPresenter(
  @Assisted private val target: ReadingBookmarkTarget,
  @Assisted private val isNested: Boolean,
  @Assisted private val onAction: (ReadingBookmarkAction) -> Unit,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranNaming: QuranNaming,
  private val quranInfo: QuranInfo,
  private val appCoroutineScope: AppCoroutineScope
) {

  @AssistedFactory
  fun interface Factory {
    fun create(
      target: ReadingBookmarkTarget,
      isNested: Boolean,
      onAction: (ReadingBookmarkAction) -> Unit
    ): ReadingBookmarkSheetPresenter
  }

  @Composable
  fun present(): ReadingBookmarkSheetState {
    val readingBookmarks = readingBookmarksDao.readingBookmarksFlow().collectAsState(null)
    val stored = remember(readingBookmarks.value) {
      readingBookmarks.value.orEmpty().associateBy { it.slot }
    }

    val isEditing = remember { mutableStateOf(false) }
    val isDismissed = remember { mutableStateOf(false) }
    val draftNames = remember { mutableStateOf<Map<ReadingBookmarkType, String>>(emptyMap()) }

    val slots = remember(stored, draftNames.value, target) {
      ReadingBookmarkType.entries.map { slot ->
        val row = stored[slot]
        ReadingBookmarkSlotItem(
          slot = slot,
          name = row?.name,
          draftName = draftNames.value[slot] ?: row?.name.orEmpty(),
          bookmark = row?.takeIf { it !is EmptyReadingBookmark },
          isAtTarget = row?.isAt(target) == true
        )
      }.toImmutableList()
    }

    fun commitNames() {
      val drafts = draftNames.value
      draftNames.value = emptyMap()
      drafts.forEach { (slot, draft) ->
        val name = draft.trim().ifBlank { null }
        if (name != stored[slot]?.name) {
          appCoroutineScope.launch { readingBookmarksDao.renameReadingBookmark(slot, name) }
        }
      }
    }

    val eventSink: (ReadingBookmarkSheetEvent) -> Unit = { event ->
      when (event) {
        ReadingBookmarkSheetEvent.StartEditing -> isEditing.value = true

        ReadingBookmarkSheetEvent.StopEditing -> {
          commitNames()
          isEditing.value = false
        }

        is ReadingBookmarkSheetEvent.NameChanged -> {
          draftNames.value += event.slot to event.name
        }

        is ReadingBookmarkSheetEvent.PlaceSlot -> {
          isDismissed.value = true
          onAction(ReadingBookmarkAction.Place(event.slot, target))
        }

        is ReadingBookmarkSheetEvent.ClearSlot -> {
          isDismissed.value = true
          onAction(ReadingBookmarkAction.Clear(event.slot))
        }

        ReadingBookmarkSheetEvent.Dismiss -> {
          commitNames()
          isDismissed.value = true
        }
      }
    }

    return ReadingBookmarkSheetState(
      target = target,
      slots = slots,
      isNested = isNested,
      isEditing = isEditing.value,
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
