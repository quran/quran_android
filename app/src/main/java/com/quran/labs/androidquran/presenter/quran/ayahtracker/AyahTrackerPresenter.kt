package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.app.Activity
import android.view.MotionEvent
import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.HighlightInfo
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.DOUBLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.LONG_PRESS
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.SINGLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.model.BookmarkModel
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.reading.common.AudioEventPresenter
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@QuranPageScope
class AyahTrackerPresenter @Inject constructor(
  private val quranInfo: QuranInfo,
  private val quranFileUtils: QuranFileUtils,
  private val quranDisplayData: QuranDisplayData,
  private val quranSettings: QuranSettings,
  private val readingEventPresenter: ReadingEventPresenter,
  private val bookmarkModel: BookmarkModel,
  private val audioEventPresenter: AudioEventPresenter,
) : AyahTracker, Presenter<AyahInteractionHandler> {
  // we may bind and unbind several times, and each time we unbind, we cancel
  // the scope, which means we can't launch new coroutines in that same scope.
  // thus, leave this as a var so we replace it every time.
  private lateinit var scope: CoroutineScope

  private var items: Array<AyahTrackerItem> = emptyArray()
  private var pendingHighlightInfo: HighlightInfo? = null
  private var lastHighlightedAyah: SuraAyah? = null

  private fun subscribe() {
    readingEventPresenter.ayahSelectionFlow
      .onEach { onAyahSelectionChanged(it) }
      .launchIn(scope)

    audioEventPresenter.audioPlaybackAyahFlow
      .onEach { onAudioSelectionChanged(it) }
      .launchIn(scope)

    items.forEach { trackerItem ->
      bookmarkModel.bookmarksForPage(trackerItem.page)
        .onEach { bookmarks -> onBookmarksChanged(bookmarks) }
        .launchIn(scope)
    }
  }

  fun setPageBounds(pageCoordinates: PageCoordinates) {
    for (item in items) {
      item.onSetPageBounds(pageCoordinates)
    }
  }

  fun setAyahCoordinates(ayahCoordinates: AyahCoordinates) {
    for (item in items) {
      item.onSetAyahCoordinates(ayahCoordinates)
    }

    val pendingHighlightInfo = pendingHighlightInfo
    if (pendingHighlightInfo != null && ayahCoordinates.ayahCoordinates.isNotEmpty()) {
      highlightAyah(
        pendingHighlightInfo.sura, pendingHighlightInfo.ayah,
        pendingHighlightInfo.highlightType, pendingHighlightInfo.scrollToAyah
      )
    }
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val startSuraAyah = ayahSelection.startSuraAyah()

    // optimization - if the current ayah is still highlighted, don't issue a request
    // to unhighlight.
    if (startSuraAyah != lastHighlightedAyah) {
      unHighlightAyahs(HighlightType.SELECTION)
      lastHighlightedAyah = startSuraAyah
    }

    when (ayahSelection) {
      is AyahSelection.Ayah -> {
        val suraAyah = ayahSelection.suraAyah
        highlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.SELECTION, false)
      }
      is AyahSelection.AyahRange -> {
        items.forEach {
          val elements = quranDisplayData.getAyahKeysOnPage(
            it.page,
            ayahSelection.startSuraAyah,
            ayahSelection.endSuraAyah
          )
          it.onHighlightAyat(it.page, elements, HighlightType.SELECTION)
        }
      }
      else -> { /* nothing is selected, and we already cleared */ }
    }
  }

  private fun onAudioSelectionChanged(suraAyah: SuraAyah?) {
    unHighlightAyahs(HighlightType.AUDIO)
    if (suraAyah != null) {
      highlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.AUDIO, true)
    }
  }

  private fun onBookmarksChanged(bookmarks: List<Bookmark>) {
    unHighlightAyahs(HighlightType.BOOKMARK)
    if (quranSettings.shouldHighlightBookmarks()) {
      items.forEach { tracker ->
        val elements = bookmarks
          .filter { it.page == tracker.page }
          .map { "${it.sura}:${it.ayah}" }
          .toSet()
        tracker.onHighlightAyat(tracker.page, elements, HighlightType.BOOKMARK)
      }
    }
  }

  private fun highlightAyah(sura: Int, ayah: Int, type: HighlightType, scrollToAyah: Boolean) {
    var handled = false
    val page = if (items.size == 1) items[0].page else quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      handled = handled || item.onHighlightAyah(page, sura, ayah, type, scrollToAyah)
    }
    pendingHighlightInfo = if (!handled) {
      HighlightInfo(sura, ayah, type, scrollToAyah)
    } else {
      null
    }
  }

  private fun unHighlightAyahs(type: HighlightType) {
    for (item in items) {
      item.onUnHighlightAyahType(type)
    }
  }

  override fun getToolBarPosition(sura: Int, ayah: Int): SelectionIndicator {
    val page = if (items.size == 1) items[0].page else quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      val position = item.getToolBarPosition(page, sura, ayah)
      if (position != SelectionIndicator.None) {
        return position
      }
    }
    return SelectionIndicator.None
  }

  override fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo? {
    for (item in items) {
      val quranAyahInfo = item.getQuranAyahInfo(sura, ayah)
      if (quranAyahInfo != null) {
        return quranAyahInfo
      }
    }
    return null
  }

  override fun getLocalTranslations(): Array<LocalTranslation>? {
    for (item in items) {
      val localTranslations = item.getLocalTranslations()
      if (localTranslations != null) {
        return localTranslations
      }
    }
    return null
  }

  fun onPressIgnoringSelectionState() {
    readingEventPresenter.onClick()
  }

  fun onLongPress(suraAyah: SuraAyah) {
    handleLongPress(suraAyah)
  }

  fun endAyahMode() {
    readingEventPresenter.onAyahSelection(AyahSelection.None)
  }

  fun handleTouchEvent(
    activity: Activity,
    event: MotionEvent,
    eventType: EventType,
    page: Int,
    ayahCoordinatesError: Boolean
  ): Boolean {
    if (eventType === DOUBLE_TAP) {
      readingEventPresenter.onAyahSelection(AyahSelection.None)
    } else if (eventType == LONG_PRESS ||
      readingEventPresenter.currentAyahSelection() != AyahSelection.None
    ) {
      // press or long press when an ayah is selected
      if (ayahCoordinatesError) {
        checkCoordinateData(activity)
      } else {
        // either a press or a long press, but we're in selection mode
        handleAyahSelection(event, eventType, page)
      }
    } else {
      // normal click
      readingEventPresenter.onClick()
    }
    return true
  }

  private fun handleAyahSelection(
    ev: MotionEvent,
    eventType: EventType,
    page: Int
  ) {
    val result = getAyahForPosition(page, ev.x, ev.y)
    if (result != null) {
      if (eventType == SINGLE_TAP) {
        readingEventPresenter.onAyahSelection(
          AyahSelection.Ayah(result, getToolBarPosition(result.sura, result.ayah))
        )
      } else if (eventType == LONG_PRESS) {
        handleLongPress(result)
      }
    }
  }

  private fun handleLongPress(selectedSuraAyah: SuraAyah) {
    val current = readingEventPresenter.currentAyahSelection()
    val updatedAyahSelection = updateAyahRange(selectedSuraAyah, current)
    readingEventPresenter.onAyahSelection(updatedAyahSelection)
  }

  private fun updateAyahRange(selectedAyah: SuraAyah, ayahSelection: AyahSelection): AyahSelection {
    val (startAyah, endAyah) = when (ayahSelection) {
      is AyahSelection.None -> selectedAyah to null
      is AyahSelection.Ayah -> {
        if (selectedAyah > ayahSelection.suraAyah) {
          ayahSelection.suraAyah to selectedAyah
        } else {
          selectedAyah to ayahSelection.suraAyah
        }
      }
      is AyahSelection.AyahRange -> {
        if (selectedAyah > ayahSelection.startSuraAyah) {
          ayahSelection.startSuraAyah to selectedAyah
        } else {
          selectedAyah to ayahSelection.startSuraAyah
        }
      }
    }

    val toolBarPosition = getToolBarPosition(selectedAyah.sura, selectedAyah.ayah)
    return if (endAyah == null) {
      AyahSelection.Ayah(startAyah, toolBarPosition)
    } else {
      AyahSelection.AyahRange(startAyah, endAyah, toolBarPosition)
    }
  }

  private fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? {
    for (item in items) {
      val ayah = item.getAyahForPosition(page, x, y)
      if (ayah != null) {
        return ayah
      }
    }
    return null
  }

  private fun checkCoordinateData(activity: Activity) {
    if (activity is PagerActivity &&
      (!quranFileUtils.haveAyaPositionFile(activity) ||
          !quranFileUtils.hasArabicSearchDatabase())
    ) {
      activity.showGetRequiredFilesDialog()
    }
  }

  override fun bind(what: AyahInteractionHandler) {
    items = what.ayahTrackerItems
    scope = MainScope()
    subscribe()
  }

  override fun unbind(what: AyahInteractionHandler) {
    items = emptyArray()
    scope.cancel()
  }

  interface AyahInteractionHandler {
    val ayahTrackerItems: Array<AyahTrackerItem>
  }
}
