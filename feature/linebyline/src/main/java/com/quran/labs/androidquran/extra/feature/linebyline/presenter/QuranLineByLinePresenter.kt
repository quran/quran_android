package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.quran.analytics.CrashReporter
import com.quran.data.core.QuranFileManager
import com.quran.data.core.QuranPageInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.data.model.selection.endSuraAyah
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.data.model.selection.withSelectionIndicator
import com.quran.data.model.selection.withYScroll
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.extra.feature.linebyline.model.DisplayText
import com.quran.labs.androidquran.extra.feature.linebyline.model.EmptyPageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightAyah
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType
import com.quran.labs.androidquran.extra.feature.linebyline.model.LineModel
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineDirection
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineModel
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection.SelectionHelper
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection.mergeWith
import com.quran.mobile.linebyline.data.model.PageModel
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class QuranLineByLinePresenter @Inject constructor(
  private val quranFileManager: QuranFileManager,
  private val pageModel: PageModel,
  private val pageProvider: PageProvider,
  private val bookmarksDao: BookmarksDao,
  private val readingEventPresenter: ReadingEventPresenter,
  private val audioStatusRepository: AudioStatusRepository,
  private val selectionHelper: SelectionHelper,
  private val quranPageInfo: QuranPageInfo,
  private val settings: Settings,
  private val lineByLineSettings: QuranLineByLineSettingsPresenter,
  private val crashReporter: CrashReporter,
  pages: IntArray
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val page = pages[0]
  private val ayahHighlightFlow =
    pageModel.ayahHighlight(pages[0])
      .stateIn(scope, SharingStarted.Lazily, emptyList())

  private var lastXOffset: Float = 0f
  private var lastYOffset: Float = 0f
  private var selectionStartSuraAyah: SuraAyah? = null

  fun loadPage(): Flow<PageInfo> {
    return combine(
      linesForPage(page),
      pageModel.suraHeaders(page),
      pageModel.ayahMarkers(page),
      pageSelection(),
      lineByLineSettings.displaySettingsFlow
    ) { lines, suraHeaders, ayahMarkers, highlights, displaySettings ->
      val displayText = DisplayText(
        quranPageInfo.suraName(page),
        quranPageInfo.juz(page),
        quranPageInfo.displayRub3(page),
        manzilText = quranPageInfo.manzilForPage(page),
        localizedPageText = quranPageInfo.localizedPage(page)
      )

      val showSidelines = displaySettings.showSidelines && pageProvider.getDataSource().haveSidelines
      val sidelines = if (showSidelines) {
        sidelinesForPage(page)
      } else {
        persistentListOf()
      }

      val scrollItem = highlights.firstOrNull { it.shouldScroll }
      val targetScrollPosition = if (scrollItem != null) {
        selectionHelper.yForLine(scrollItem.ayahHighlights.first().lineId)
      } else {
        -1
      }
      PageInfo(
        page,
        pageProvider.pageType(),
        displayText,
        displaySettings,
        lines,
        suraHeaders,
        ayahMarkers,
        highlights,
        sidelines,
        targetScrollPosition,
        showSidelines,
        quranPageInfo.skippedPagesCount()
      )
    }
  }

  fun emptyState(): PageInfo {
    return EmptyPageInfo.copy(displaySettings = lineByLineSettings.latestDisplaySettings())
  }

  fun onClick() {
    readingEventPresenter.onClick()
    if (readingEventPresenter.ayahSelectionFlow.value !is AyahSelection.None) {
      readingEventPresenter.onAyahSelection(AyahSelection.None)
    }
  }

  fun startSelection(x: Float, y: Float) {
    selectionStartSuraAyah = null
    selectionHelper.startSelection(x, y)
    modifySelectionRange(0f, 0f)
  }

  fun onPagePositioned(x: Float, y: Float, width: Int, height: Int) {
    selectionHelper.setPageDimensions(width, height)
    val changeScrollY = y != lastYOffset
    lastXOffset = x
    lastYOffset = y

    if (changeScrollY) {
      applyUpdatedSelectionScrollY(y)
    }
  }

  fun modifySelectionRange(offsetX: Float, offsetY: Float) {
    val previousSelection = readingEventPresenter.ayahSelectionFlow.value
    val selectedAyah = selectionHelper.modifySelectionRange(offsetX, offsetY, ayahHighlightFlow.value)
    if (selectedAyah != null) {
      val selectionStart = selectionStartSuraAyah ?: selectedAyah
      selectionStartSuraAyah = selectionStart

      val updatedSelection =
        previousSelection.mergeWith(AyahSelection.Ayah(selectedAyah), selectionStart)
      if (updatedSelection != previousSelection) {
        // clear the selection when the page changes for a selection range
        val clearSelection = doesSpanMultiplePages(updatedSelection)
        val selectionToUpdate =
          if (clearSelection) { updatedSelection.withSelectionIndicator(SelectionIndicator.None) }
          else { updatedSelection }
        readingEventPresenter.onAyahSelection(selectionToUpdate)
      }
    }
  }

  private fun doesSpanMultiplePages(ayahSelection: AyahSelection): Boolean {
    return if (ayahSelection is AyahSelection.AyahRange) {
      val startSuraAyah = ayahSelection.startSuraAyah
      val endSuraAyah = ayahSelection.endSuraAyah
      val start = quranPageInfo.pageForSuraAyah(startSuraAyah.sura, startSuraAyah.ayah)
      val end = quranPageInfo.pageForSuraAyah(endSuraAyah.sura, endSuraAyah.ayah)
      start != end
    } else {
      false
    }
  }

  fun endSelection() {
    selectionStartSuraAyah = null
    selectionHelper.endSelection()
    val currentSelection = readingEventPresenter.ayahSelectionFlow.value
    if (currentSelection !is AyahSelection.None &&
      (currentSelection.selectionIndicator() is SelectionIndicator.None ||
          currentSelection.selectionIndicator() is SelectionIndicator.ScrollOnly)
    ) {
      val bounds = boundsFor(currentSelection)
      if (bounds != null) {
        val (firstBounds, lastBounds) = bounds
        val updated = currentSelection.withSelectionIndicator(
          SelectionIndicator.SelectedItemPosition(
            firstBounds,
            lastBounds,
            yScroll = lastYOffset
          )
        )

        if (updated != currentSelection) {
          readingEventPresenter.onAyahSelection(updated)
        }
      }
    }
  }

  private fun applyUpdatedSelectionScrollY(y: Float) {
    val currentSelection = readingEventPresenter.ayahSelectionFlow.value
    val selectionIndicator = currentSelection.selectionIndicator()
    if (selectionIndicator !is SelectionIndicator.None && selectionIndicator !is SelectionIndicator.ScrollOnly) {
      val updatedSelectionIndicator = selectionIndicator.withYScroll(y)
      val updatedSelection = currentSelection.withSelectionIndicator(updatedSelectionIndicator)
      readingEventPresenter.onAyahSelection(updatedSelection)
    }
  }

  private fun boundsFor(selection: AyahSelection): Pair<SelectionRectangle, SelectionRectangle>? {
    val startAyah = selection.startSuraAyah() ?: return null
    val endAyah = selection.endSuraAyah() ?: return null

    val highlights = ayahHighlightFlow.value
    val initialStartBounds = highlights.firstOrNull { it.sura == startAyah.sura && it.ayah == startAyah.ayah }
    val initialEndBounds = highlights.lastOrNull { it.sura == endAyah.sura && it.ayah == endAyah.ayah }

    // if the selection spans multiple pages, we might have either a start or
    // an end - use the one we have for the other one in this case.
    val startBounds = initialStartBounds ?: initialEndBounds
    val endBounds = initialEndBounds ?: initialStartBounds

    return if (startBounds != null && endBounds != null) {
      val startRectangle = selectionHelper.selectionRectangle(startBounds)
      val endRectangle = selectionHelper.selectionRectangle(endBounds)
      if (startRectangle != null && endRectangle != null) {
        // for now only offset by x, we'll use scrollY for y
        // when tablet is implemented, we should reconsider
        startRectangle.offset(lastXOffset, 0f) to
            endRectangle.offset(lastXOffset, 0f)
      } else {
        null
      }
    } else {
      null
    }
  }

  private fun pageSelection(): Flow<ImmutableList<HighlightAyah>> {
    return combine(
      ayahHighlightFlow,
      bookmarksDao.bookmarksForPage(page),
      readingEventPresenter.ayahSelectionFlow,
      audioStatusRepository.audioPlaybackFlow,
    ) { ayahHighlights, bookmarks, selectedAyah, audioPlaybackStatus ->
      val currentBookmarkHighlights = bookmarks.map {
          bookmark -> ayahHighlights.filter { it.sura == bookmark.sura && it.ayah == bookmark.ayah }
      }.map { HighlightAyah(HighlightType.BOOKMARK, it) }

      val bookmarkHighlights =
        if (currentBookmarkHighlights.isEmpty() || settings.shouldShowBookmarks()) {
          currentBookmarkHighlights
        }
        else {
          emptyList()
        }

      val selectedAyahHighlights =
        when (selectedAyah) {
          is AyahSelection.Ayah -> {
            val suraAyah = selectedAyah.suraAyah
            ayahHighlights.filter { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
          }
          is AyahSelection.AyahRange -> {
            val start = selectedAyah.startSuraAyah
            val end = selectedAyah.endSuraAyah
            ayahHighlights.filter { isAyahWithin(it.sura, it.ayah, start, end) }
          }
          AyahSelection.None -> emptyList()
        }
      val shouldScroll = selectedAyah is AyahSelection.Ayah &&
          selectedAyah.selectionIndicator is SelectionIndicator.ScrollOnly
      val selectedHighlights = HighlightAyah(HighlightType.SELECTION, selectedAyahHighlights, shouldScroll)

      val audioPlaybackAyah = audioPlaybackStatus.currentPlaybackAyah()
      val audioHighlight = if (audioPlaybackAyah == null) {
        emptyList()
      } else {
        ayahHighlights.filter {
          it.sura == audioPlaybackAyah.sura && it.ayah == audioPlaybackAyah.ayah
        }
      }
      val audioHighlights = HighlightAyah(HighlightType.AUDIO, audioHighlight, true)

      (bookmarkHighlights + selectedHighlights + audioHighlights)
        .filter { it.ayahHighlights.isNotEmpty() }
        .toImmutableList()
    }
  }

  private fun isAyahWithin(sura: Int, ayah: Int, start: SuraAyah, end: SuraAyah): Boolean {
    return SuraAyah(sura, ayah) in start..end
  }

  private fun linesForPage(page: Int): Flow<ImmutableList<LineModel>> {
    return flow {
      val lines = mutableListOf<LineModel>()
      val whence = quranFileManager.quranImagesDirectory()
      for (lineNumber in 1..15) {
        val file = File(File(whence, page.toString()), "$lineNumber.png")
        try {
          val fileName = file.toString()
          val sourceBitmap = BitmapFactory.decodeFile(fileName, options)
          val bitmap = sourceBitmap.extractAlpha()
          sourceBitmap.recycle()
          lines.add(LineModel(lineNumber - 1, bitmap.asImageBitmap()))
        } catch (exception: Exception) {
          val status = if (file.exists()) { "exists" } else { "missing" }
          // make sure to add the line number and page to the crash metadata
          crashReporter.log("Failed to load line $lineNumber for page $page - file $status")
          throw exception
        }
      }
      emit(lines.toImmutableList())
    }
  }

  private suspend fun sidelinesForPage(page: Int): ImmutableList<SidelineModel> {
    return withContext(Dispatchers.IO) {
      val whence = quranFileManager.quranImagesDirectory()
      val direction = if (page % 2 == 0) SidelineDirection.DOWN else SidelineDirection.UP
      val sidelinesDirectory = File(File(whence, page.toString()), "sidelines")
      if (sidelinesDirectory.isDirectory) {
        val files = sidelinesDirectory.listFiles()
          ?.filter {
            val filenameWithoutOverrides =
              it.nameWithoutExtension.replace("_down", "").replace("_up", "")
            it.extension == "png" &&
                filenameWithoutOverrides.toIntOrNull() != null
          } ?: emptyList()
        files.map {
          val filenameWithoutOverrides =
            it.nameWithoutExtension.replace("_down", "").replace("_up", "")
          val number = filenameWithoutOverrides.toInt()

          val actualDirection = if ("_up" in it.nameWithoutExtension) {
            SidelineDirection.UP
          } else if ("_down" in it.nameWithoutExtension) {
            SidelineDirection.DOWN
          } else {
            direction
          }

          val sourceBitmap = BitmapFactory.decodeFile(it.toString(), options)
          val bitmap = sourceBitmap.extractAlpha()
          sourceBitmap.recycle()
          SidelineModel(bitmap.asImageBitmap(), number, actualDirection)
        }.toImmutableList()
      } else {
        persistentListOf()
      }
    }
  }

  companion object {
    private val options = BitmapFactory.Options()
      .apply {
        inPreferredConfig = Bitmap.Config.ALPHA_8
      }
  }
}
