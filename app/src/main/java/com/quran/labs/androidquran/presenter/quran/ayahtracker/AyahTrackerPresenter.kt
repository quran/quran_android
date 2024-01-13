package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.app.Activity
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.ImageView
import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.AyahWord
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.highlight.HighlightInfo
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.SuraAyahIterator
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.DOUBLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.LONG_PRESS
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.SINGLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.model.BookmarkModel
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.reading.common.ReadingEventPresenter
import com.quran.recitation.events.RecitationEventPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction
import com.quran.recitation.presenter.RecitationHighlightsPresenter.RecitationPage
import com.quran.recitation.presenter.RecitationPopupPresenter
import com.quran.recitation.presenter.RecitationPopupPresenter.PopupContainer
import com.quran.recitation.presenter.RecitationPresenter
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
  private val audioStatusRepository: AudioStatusRepository,
  recitationPresenter: RecitationPresenter,
  private val recitationEventPresenter: RecitationEventPresenter,
  private val recitationPopupPresenter: RecitationPopupPresenter,
  private val recitationHighlightsPresenter: RecitationHighlightsPresenter,
) : AyahTracker, Presenter<AyahInteractionHandler>, PopupContainer, RecitationPage {
  // we may bind and unbind several times, and each time we unbind, we cancel
  // the scope, which means we can't launch new coroutines in that same scope.
  // thus, leave this as a var so we replace it every time.
  private lateinit var scope: CoroutineScope

  private var items: Array<AyahTrackerItem> = emptyArray()
  private var pendingHighlightInfo: HighlightInfo? = null
  private var lastHighlightedAyah: SuraAyah? = null
  private var lastHighlightedAudioAyah: SuraAyah? = null

  private val isRecitationEnabled = recitationPresenter.isRecitationEnabled()

  private fun subscribe() {
    readingEventPresenter.ayahSelectionFlow
      .onEach { onAyahSelectionChanged(it) }
      .launchIn(scope)

    audioStatusRepository.audioPlaybackFlow
      .onEach { onAudioSelectionChanged(it.currentPlaybackAyah()) }
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
        pendingHighlightInfo.sura, pendingHighlightInfo.ayah, pendingHighlightInfo.word,
        pendingHighlightInfo.highlightType, pendingHighlightInfo.scrollToAyah
      )
    }

    if (isRecitationEnabled) {
      recitationHighlightsPresenter.refresh()
    }
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val startSuraAyah = ayahSelection.startSuraAyah()

    // optimization - if the current ayah is still highlighted, don't issue a request
    // to unhighlight.
    if (startSuraAyah != lastHighlightedAyah) {
      unHighlightAyahs(HighlightTypes.SELECTION)
      lastHighlightedAyah = startSuraAyah
    }

    when (ayahSelection) {
      is AyahSelection.Ayah -> {
        val suraAyah = ayahSelection.suraAyah
        val scrollToAyah = ayahSelection.selectionIndicator == SelectionIndicator.ScrollOnly
        highlightAyah(suraAyah.sura, suraAyah.ayah, -1, HighlightTypes.SELECTION, scrollToAyah)
      }
      is AyahSelection.AyahRange -> {
        val highlightAyatIterator =
          SuraAyahIterator(quranInfo, ayahSelection.startSuraAyah, ayahSelection.endSuraAyah)
        val highlightedAyat = highlightAyatIterator.asSet()
        items.forEach {
          val pageAyat = quranDisplayData.getAyahKeysOnPage(it.page)
          val elements = pageAyat.intersect(highlightedAyat)
          if (elements.isNotEmpty()) {
            it.onHighlightAyat(it.page, elements, HighlightTypes.SELECTION)
          }
        }
      }
      else -> { /* nothing is selected, and we already cleared */ }
    }
  }

  private fun onAudioSelectionChanged(suraAyah: SuraAyah?) {
    val currentLastHighlightAudioAyah = lastHighlightedAudioAyah

    // if there is no currently highlighted audio ayah, go ahead and clear all
    if (currentLastHighlightAudioAyah == null) {
      unHighlightAyahs(HighlightTypes.AUDIO)
    }

    // either way, whether we unhighlighted all or not, highlight the new ayah
    if (suraAyah != null) {
      highlightAyah(suraAyah.sura, suraAyah.ayah, -1, HighlightTypes.AUDIO, true)
    }

    // if we had a highlighted ayah before, unhighlight it now.
    // we do this *after* highlighting the new ayah so that the animations continue working.
    if (suraAyah != currentLastHighlightAudioAyah && currentLastHighlightAudioAyah != null) {
      val sura = currentLastHighlightAudioAyah.sura
      val ayah = currentLastHighlightAudioAyah.ayah
      val page = quranInfo.getPageFromSuraAyah(sura, ayah)

      items.filter { it.page == page }
        .onEach {
          it.onUnHighlightAyah(page, sura, ayah, -1, HighlightTypes.AUDIO)
        }
    }

    // and keep track of the last highlighted audio ayah
    lastHighlightedAudioAyah = suraAyah
  }

  private fun onBookmarksChanged(bookmarks: List<Bookmark>) {
    unHighlightAyahs(HighlightTypes.BOOKMARK)
    if (quranSettings.shouldHighlightBookmarks()) {
      items.forEach { tracker ->
        val elements = bookmarks
          .filter { it.page == tracker.page }
          .map { "${it.sura}:${it.ayah}" }
          .toSet()
        if (elements.isNotEmpty()) {
          tracker.onHighlightAyat(tracker.page, elements, HighlightTypes.BOOKMARK)
        }
      }
    }
  }

  private fun highlightAyah(sura: Int, ayah: Int, word: Int, type: HighlightType, scrollToAyah: Boolean) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    val handled = items.any {
      it.page == page && it.onHighlightAyah(page, sura, ayah, word, type, scrollToAyah)
    }

    pendingHighlightInfo = if (!handled) {
      HighlightInfo(sura, ayah, word, type, scrollToAyah)
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
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      val position = item.getToolBarPosition(page, sura, ayah)
      if (position != SelectionIndicator.None && position != SelectionIndicator.ScrollOnly) {
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
    } else if (eventType == SINGLE_TAP && recitationEventPresenter.hasRecitationSession()) {
      handleTap(event, eventType, page)
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

  private fun handleTap(
    ev: MotionEvent,
    eventType: EventType,
    page: Int
  ) {
    for (item in items) {
      val glyph = item.getGlyphForPosition(page, ev.x, ev.y) ?: continue

      val portion = when (glyph) {
        is WordGlyph -> glyph.toAyahWord()
        is AyahEndGlyph -> glyph.ayah
        else -> glyph.ayah
      }

      readingEventPresenter.onClick(portion)
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
    items = what.getAyahTrackerItems()
    scope = MainScope()
    if (isRecitationEnabled) {
      recitationPopupPresenter.bind(this)
      recitationHighlightsPresenter.bind(this)
    }
    subscribe()
  }

  override fun unbind(what: AyahInteractionHandler) {
    if (isRecitationEnabled) {
      recitationHighlightsPresenter.unbind(this)
      recitationPopupPresenter.unbind(this)
    }
    items = emptyArray()
    scope.cancel()
  }

  interface AyahInteractionHandler {
    fun getAyahTrackerItems(): Array<AyahTrackerItem>
  }

  // PopupContainer <--> AyahTrackerItem adapter

  override fun getQuranPageImageView(page: Int): ImageView? {
    val item = items.firstOrNull { it.page == page } ?: return null
    val ayahView = item.getAyahView() ?: return null
    return ayahView as? ImageView ?: return null
  }

  override fun getSelectionBoundsForWord(
    page: Int,
    word: AyahWord
  ): SelectionIndicator.SelectedItemPosition? {
    val item = items.firstOrNull { it.page == page } ?: return null
    val selection = item.getToolBarPosition(item.page, word) as? SelectionIndicator.SelectedItemPosition ?: return null
    return selection
  }

  override fun getBoundsForWord(word: AyahWord): List<RectF>? {
    return (items[0] as? AyahImageTrackerItem)?.pageGlyphsCoords?.getBoundsForWord(word)
  }

  // RecitationPage <--> AyahTrackerItem adapter

  override val pageNumbers: Set<Int>
    get() = items.map { it.page }.toSet()

  override fun applyHighlights(highlights: List<HighlightAction>) {
    highlights.forEach {
      when (it) {
        is HighlightAction.Highlight -> highlight(it.highlightInfo)
        is HighlightAction.Unhighlight -> unhighlight(it.highlightInfo)
        is HighlightAction.UnhighlightAll -> unHighlightAyahs(it.highlightType, it.page)
      }
    }
  }

  private fun highlight(highlightInfo: HighlightInfo) {
    val page = quranInfo.getPageFromSuraAyah(highlightInfo.sura, highlightInfo.ayah)
    items.asSequence()
      .filter { it.page == page }
      .forEach { it.onHighlight(page, highlightInfo) }
  }

  private fun unhighlight(highlightInfo: HighlightInfo) {
    val page = quranInfo.getPageFromSuraAyah(highlightInfo.sura, highlightInfo.ayah)
    items.asSequence()
      .filter { it.page == page }
      .forEach { it.onUnhighlight(page, highlightInfo) }
  }

  private fun unHighlightAyahs(type: HighlightType, page: Int? = null) {
    items.asSequence()
      .filter { page == null || page == it.page }
      .forEach { it.onUnHighlightAyahType(type) }
  }

}
