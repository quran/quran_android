package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.os.Build
import android.text.TextPaint
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.asImageBitmap
import com.quran.analytics.CrashReporter
import com.quran.data.core.QuranFileManager
import com.quran.data.core.QuranInfo
import com.quran.data.core.QuranPageInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.Settings
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.data.model.selection.endSuraAyah
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.data.model.selection.withSelectionIndicator
import com.quran.data.model.selection.withYScroll
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackWord
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.drawing.R as DrawingR
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.extra.feature.linebyline.model.DisplaySettings
import com.quran.labs.androidquran.extra.feature.linebyline.model.DisplayText
import com.quran.labs.androidquran.extra.feature.linebyline.model.EmptyPageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightAyah
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType
import com.quran.labs.androidquran.extra.feature.linebyline.model.MissingLineByLineImagesException
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.ReadingBookmarkAyah
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineDirection
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineModel
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection.SelectionHelper
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection.mergeWith
import com.quran.labs.androidquran.extra.feature.linebyline.resource.ImageBitmapUtil
import com.quran.labs.androidquran.extra.feature.linebyline.resource.LineBitmapRecycler
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import com.quran.mobile.linebyline.data.dao.WordHighlight
import com.quran.mobile.linebyline.data.dao.asAyahHighlight
import com.quran.mobile.linebyline.data.model.PageModel
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranPageCalculator
import com.quran.mobile.mapper.imlaei.data.ImlaeiUthmaniMapper
import com.quran.mobile.mapper.imlaei.data.WordAlignment
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class QuranLineByLinePresenter @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager,
  private val pageModel: PageModel,
  private val pageProvider: PageProvider,
  private val bookmarksDao: BookmarksDao,
  private val highlightsDao: HighlightsDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val readingEventPresenter: ReadingEventPresenter,
  private val audioStatusRepository: AudioStatusRepository,
  private val selectionHelper: SelectionHelper,
  private val quranPageInfo: QuranPageInfo,
  private val settings: Settings,
  private val lineByLineSettings: QuranLineByLineSettingsPresenter,
  private val crashReporter: CrashReporter,
  private val quranInfo: QuranInfo,
  private val imlaeiUthmaniMapper: ImlaeiUthmaniMapper,
  private val imageBitmapUtil: ImageBitmapUtil,
  pages: IntArray
) {
  @Volatile
  private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Volatile
  private var page = pages[0]

  private var lastXOffset: Float = 0f
  private var lastYOffset: Float = 0f
  private var selectionStartSuraAyah: SuraAyah? = null

  private data class PageDetails(
    val suraHeaders: ImmutableList<SuraHeader>,
    val ayahMarkers: ImmutableList<AyahMarkerInfo>,
    val ayahBounds: ImmutableList<AyahHighlight>,
    val wordBounds: ImmutableList<WordHighlight>
  )

  private data class CompositedPageKey(
    val isNightMode: Boolean,
    val textBrightness: Int,
    val showDividers: Boolean,
    val lineColor: Int,
    val headerColor: Int,
    val ayahColor: Int,
    val ringColor: Int,
    val innerColor: Int,
    val targetWidth: Int,
    val targetHeight: Int
  )

  private val trackedSidelineBitmaps = mutableListOf<Bitmap>()
  @Volatile
  private var cachedSidelines: ImmutableList<SidelineModel>? = null
  private val cachedWordMappings = AtomicReference<List<WordAlignment>?>(null)

  private val compositingMutex = Mutex()
  @Volatile
  private var compositedKey: CompositedPageKey? = null
  @Volatile
  private var compositedBitmap: Bitmap? = null
  @Volatile
  private var isDetached = false

  @Volatile
  private var pageDetailsDeferred: Deferred<PageDetails>? = null

  @Synchronized
  fun attach() {
    isDetached = false
    if (!scope.isActive) {
      scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
  }

  @Synchronized
  fun releaseCompositedBitmaps() {
    cachedSidelines = null

    val oldComposited = compositedBitmap
    compositedBitmap = null
    compositedKey = null

    val bitmapsToRecycle = ArrayList<Bitmap>(8)
    if (oldComposited != null) {
      bitmapsToRecycle.add(oldComposited)
    }
    synchronized(trackedSidelineBitmaps) {
      bitmapsToRecycle.addAll(trackedSidelineBitmaps)
      trackedSidelineBitmaps.clear()
    }
    bitmapsToRecycle.forEach { LineBitmapRecycler.scheduleRecycle(it) }
  }

  @Synchronized
  fun detach() {
    isDetached = true
    val deferred = pageDetailsDeferred
    pageDetailsDeferred = null
    cachedWordMappings.set(null)
    releaseCompositedBitmaps()
    scope.cancel()
  }

  @Synchronized
  fun rebindPage(newPage: Int) {
    if (page == newPage && isDetached) {
      lastXOffset = 0f
      lastYOffset = 0f
      selectionStartSuraAyah = null
      return
    }
    detach()
    page = newPage
    lastXOffset = 0f
    lastYOffset = 0f
    selectionStartSuraAyah = null
  }

  @Synchronized
  private fun pageDetailsFetcher(): Deferred<PageDetails> {
    val existing = pageDetailsDeferred
    if (existing != null && !existing.isCancelled) {
      return existing
    }
    attach()
    return scope.async {
      PageDetails(
        suraHeaders = pageModel.suraHeaders(page),
        ayahMarkers = pageModel.ayahMarkers(page),
        ayahBounds = pageModel.ayahHighlight(page),
        wordBounds = pageModel.wordHighlights(page)
      )
    }.also { pageDetailsDeferred = it }
  }

  fun loadPage(): Flow<PageInfo> {
    return combine(
      pageSelection(),
      readingBookmarksForPage(),
      lineByLineSettings.displaySettingsFlow
    ) { highlights, readingBookmarks, displaySettings ->
      val displayText = DisplayText(
        quranPageInfo.suraName(page),
        quranPageInfo.juz(page),
        quranPageInfo.displayRub3(page),
        manzilText = quranPageInfo.manzilForPage(page),
        localizedPageText = quranPageInfo.localizedPage(page)
      )

      val showSidelines = displaySettings.showSidelines && pageProvider.getDataSource().haveSidelines
      val sidelines = if (showSidelines) {
        cachedSidelines ?: sidelinesForPage(page).also { cachedSidelines = it }
      } else {
        persistentListOf()
      }

      val scrollItem = highlights.firstOrNull { it.shouldScroll }
      val targetScrollPosition = if (scrollItem != null) {
        selectionHelper.yForLine(scrollItem.ayahHighlights.first().lineId)
      } else {
        -1
      }

      val pageDetails = pageDetailsFetcher().await()
      val key = computeCompositedPageKey(displaySettings)
      val pageBitmap = ensureCompositedBitmap(pageDetails, key)
      PageInfo(
        page = page,
        pageType = pageProvider.pageType(),
        displayText = displayText,
        displaySettings = displaySettings,
        compositedPageBitmap = pageBitmap,
        compositedWidth = key.targetWidth,
        compositedHeight = key.targetHeight,
        compositedIsNightMode = key.isNightMode,
        compositedTextBrightness = key.textBrightness,
        compositedShowDividers = key.showDividers,
        compositedLineColor = key.lineColor,
        compositedHeaderColor = key.headerColor,
        compositedAyahColor = key.ayahColor,
        suraHeaders = pageDetails.suraHeaders,
        ayahMarkers = pageDetails.ayahMarkers,
        ayahHighlights = highlights,
        readingBookmarks = readingBookmarks,
        sidelines = sidelines,
        targetScrollPosition = targetScrollPosition,
        showSidelines = showSidelines,
        skippedPageCount = quranPageInfo.skippedPagesCount()
      )
    }.flowOn(Dispatchers.Default)
  }

  fun emptyState(): PageInfo {
    return EmptyPageInfo.copy(displaySettings = lineByLineSettings.latestDisplaySettings())
  }

  suspend fun fallbackToImageType(): Boolean {
    val currentPageType = settings.pageType()
    val fallbackType = pageProvider.getFallbackPageType()
    return if (fallbackType != null && currentPageType != fallbackType) {
      settings.setPageType(fallbackType)
      crashReporter.log("Line-by-line fallback from $currentPageType to $fallbackType")
      true
    } else {
      false
    }
  }

  fun onClick() {
    readingEventPresenter.onClick()
    if (readingEventPresenter.ayahSelectionFlow.value !is AyahSelection.None) {
      readingEventPresenter.onAyahSelection(AyahSelection.None)
    }
  }

  suspend fun startSelection(x: Float, y: Float) {
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

  suspend fun modifySelectionRange(offsetX: Float, offsetY: Float) {
    val previousSelection = readingEventPresenter.ayahSelectionFlow.value
    val ayahHighlights = pageDetailsFetcher().await().ayahBounds
    val selectedAyah = selectionHelper.modifySelectionRange(offsetX, offsetY, ayahHighlights)
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

  suspend fun endSelection() {
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

  private suspend fun boundsFor(selection: AyahSelection): Pair<SelectionRectangle, SelectionRectangle>? {
    val startAyah = selection.startSuraAyah() ?: return null
    val endAyah = selection.endSuraAyah() ?: return null

    val highlights = pageDetailsFetcher().await().ayahBounds
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
      bookmarksDao.bookmarksForPage(page),
      highlightsForPage(),
      readingEventPresenter.ayahSelectionFlow,
      audioStatusRepository.audioPlaybackFlow,
    ) { bookmarks, highlights, selectedAyah, audioPlaybackStatus ->
      val pageDetails = pageDetailsFetcher().await()
      val ayahHighlights = pageDetails.ayahBounds
      val currentBookmarkHighlights = bookmarks.map {
          bookmark -> ayahHighlights.filter { it.sura == bookmark.sura && it.ayah == bookmark.ayah }
      }.map { HighlightAyah(HighlightType.Bookmark, it) }

      val colorHighlights = highlights.map { highlight ->
        val suraAyah = highlight.suraAyah
        HighlightAyah(
          HighlightType.Highlight(highlight.color),
          ayahHighlights.filter { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
        )
      }

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
      val selectedHighlights = HighlightAyah(HighlightType.Selection, selectedAyahHighlights, shouldScroll)

      val audioPlaybackAyah = audioPlaybackStatus.currentPlaybackAyah()
      val audioHighlight = if (audioPlaybackAyah == null) {
        emptyList()
      } else {
        ayahHighlights.filter {
          it.sura == audioPlaybackAyah.sura && it.ayah == audioPlaybackAyah.ayah
        }
      }

      val audioPlaybackWord = audioPlaybackStatus.currentPlaybackWord()
      val wordHighlights = if (audioPlaybackAyah == null || audioPlaybackWord == null) {
        emptyList()
      } else {
        val ayahWords = pageDetails.wordBounds.filter { it.sura == audioPlaybackAyah.sura && it.ayah == audioPlaybackAyah.ayah }

        // find the corresponding mapping from imlaei => uthmani, since audioWordNumber is imlaei
        val mappings = wordAlignmentMappings()
        val targetWord = mappings.find {
          it.sura == audioPlaybackAyah.sura && it.ayah == audioPlaybackAyah.ayah && it.audioWordNumber == audioPlaybackWord
        }

        // each word in imlaei might correspond to one or more words in uthmani
        targetWord?.glyphWordNumbers.orEmpty()
          .mapNotNull { ayahWords.getOrNull(it - 1) }
          .map { it.asAyahHighlight() }
      }

      val audioAyahHighlights = HighlightAyah(HighlightType.Audio, audioHighlight, wordHighlights.isEmpty())
      val audioWordHighlights = HighlightAyah(HighlightType.AudioWord, wordHighlights, true)

      (colorHighlights + bookmarkHighlights + selectedHighlights + audioAyahHighlights + audioWordHighlights)
        .filter { it.ayahHighlights.isNotEmpty() }
        .toImmutableList()
    }
  }

  private fun readingBookmarksForPage(): Flow<ImmutableList<ReadingBookmarkAyah>> {
    return readingBookmarksDao.readingBookmarksFlow()
      .map { readingBookmarks ->
        val ayahBounds = pageDetailsFetcher().await().ayahBounds
        readingBookmarks
          .filterIsInstance<AyahReadingBookmark>()
          .groupBy({ it.asSuraAyah() }, { it.slot })
          .map { (suraAyah, slots) ->
            ReadingBookmarkAyah(
              slots.sorted().toImmutableList(),
              ayahBounds.filter { it.sura == suraAyah.sura && it.ayah == suraAyah.ayah }
            )
          }
          .filter { it.ayahHighlights.isNotEmpty() }
          .toImmutableList()
      }
      .distinctUntilChanged()
  }

  private fun highlightsForPage(): Flow<List<Highlight>> {
    val range = quranInfo.getVerseRangeForPage(page)
    val startAyah = SuraAyah(range.startSura, range.startAyah)
    val endAyah = SuraAyah(range.endingSura, range.endingAyah)
    return highlightsDao.highlightsFlow()
      .map { highlights -> highlights.filter { it.suraAyah in startAyah..endAyah } }
      .distinctUntilChanged()
  }

  private fun isAyahWithin(sura: Int, ayah: Int, start: SuraAyah, end: SuraAyah): Boolean {
    return SuraAyah(sura, ayah) in start..end
  }

  private suspend fun wordAlignmentMappings(): List<WordAlignment> {
    cachedWordMappings.get()?.let { return it }
    val range = quranInfo.getVerseRangeForPage(page)
    val startAyah = SuraAyah(range.startSura, range.startAyah)
    val endAyah = SuraAyah(range.endingSura, range.endingAyah)
    val loaded = imlaeiUthmaniMapper.mapWords(startAyah, endAyah)
    cachedWordMappings.compareAndSet(null, loaded)
    return cachedWordMappings.get() ?: loaded
  }

  private fun computeCompositedPageKey(displaySettings: DisplaySettings): CompositedPageKey {
    val dm = appContext.resources.displayMetrics
    val systemNight = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val isNightMode = displaySettings.isNightMode(systemNight)
    val textBrightness = displaySettings.textBrightness.coerceIn(0, 255)
    val showDividers = displaySettings.showLineDividers && page > 3
    val lineColor = if (isNightMode) {
      Color.argb(textBrightness, 255, 255, 255)
    } else {
      Color.BLACK
    }
    val is1439 = pageProvider.pageType().contains("1439")
    val headerColor: Int
    val ringColor: Int
    val innerColor: Int
    val ayahColor: Int
    when {
      is1439 && isNightMode -> {
        headerColor = 0xFF73AFFA.toInt()
        ringColor = 0xFF73AFFA.toInt()
        innerColor = 0xFF172554.toInt()
        ayahColor = 0xFF73AFFA.toInt()
      }
      is1439 -> {
        headerColor = 0xFF2563EB.toInt()
        ringColor = 0xFF2563EB.toInt()
        innerColor = 0xFFEFF6FF.toInt()
        ayahColor = 0xFF1D4ED8.toInt()
      }
      isNightMode -> {
        headerColor = 0xFF047857.toInt()
        ringColor = 0xFF047857.toInt()
        innerColor = 0xFF022C22.toInt()
        ayahColor = 0xFF34D399.toInt()
      }
      else -> {
        headerColor = 0xFF047857.toInt()
        ringColor = 0xFF047857.toInt()
        innerColor = 0xFFECFDF5.toInt()
        ayahColor = 0xFF047857.toInt()
      }
    }

    val rawMinDim = min(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
    val rawMaxDim = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(rawMinDim)
    val targetWidth = min(1440, rawMinDim)
    val topClearance = (72f * dm.density).roundToInt()
    val availHeight = (rawMaxDim - topClearance).coerceAtLeast((rawMaxDim * 0.65f).roundToInt())
    val measurements = QuranPageCalculator().calculate(targetWidth, availHeight)
    return CompositedPageKey(
      isNightMode = isNightMode,
      textBrightness = textBrightness,
      showDividers = showDividers,
      lineColor = lineColor,
      headerColor = headerColor,
      ayahColor = ayahColor,
      ringColor = ringColor,
      innerColor = innerColor,
      targetWidth = measurements.quranImageWidth.coerceAtLeast(1),
      targetHeight = measurements.quranImageHeight.coerceAtLeast(1)
    )
  }

  private suspend fun ensureCompositedBitmap(
    details: PageDetails,
    key: CompositedPageKey
  ): Bitmap {
    val existing = compositedBitmap
    if (existing != null && !existing.isRecycled && compositedKey == key) {
      return existing
    }
    return compositingMutex.withLock {
      val lockedExisting = compositedBitmap
      if (lockedExisting != null && !lockedExisting.isRecycled && compositedKey == key) {
        return@withLock lockedExisting
      }
      val built = withContext(COMPOSITING_DISPATCHER) {
        buildCompositedPage(details, key)
      }
      if (isDetached) {
        LineBitmapRecycler.scheduleRecycle(built)
        throw CancellationException("Presenter detached during page compositing")
      }
      val previous = compositedBitmap
      compositedBitmap = built
      compositedKey = key
      if (previous != null && previous !== built) {
        LineBitmapRecycler.scheduleRecycle(previous)
      }
      built
    }
  }

  private suspend fun buildCompositedPage(
    details: PageDetails,
    key: CompositedPageKey
  ): Bitmap {
    val whence = quranFileManager.quranImagesDirectory()
    val pageDirectory = File(whence, page.toString())
    val sampleSize = if (key.targetWidth <= 720) 2 else 1
    val swBitmap = acquireScratchBitmap(key.targetWidth, key.targetHeight)
    var returnedSwBitmap = false
    try {
      swBitmap.eraseColor(Color.TRANSPARENT)
      val canvas = Canvas(swBitmap)
      val lineConfig = pageProvider.getPageContentType() as? PageContentType.Line
      val ratio = lineConfig?.ratio ?: (232f / 1440f)
      val allowLinesToOverlap = lineConfig?.allowOverlapOfLines ?: true
      val totalWidthInt = key.targetWidth
      val totalHeightInt = key.targetHeight
      val totalWidth = totalWidthInt.toFloat()
      val totalHeight = totalHeightInt.toFloat()
      val lineHeight = totalWidth * ratio
      val lineHeightInt = lineHeight.toInt()
      val lineCount = 15
      val layoutLineHeight = if (allowLinesToOverlap) lineHeightInt else totalHeightInt / lineCount
      val layoutLastLineIndex = (lineCount - 1).toFloat()

      val linePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        color = key.lineColor
      }
      val argbLinePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        color = key.lineColor
        colorFilter = PorterDuffColorFilter(key.lineColor, PorterDuff.Mode.SRC_IN)
      }
      val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = key.lineColor
        strokeWidth = 1f
      }
      val dstRect = Rect()

      val hdrBitmap = if (details.suraHeaders.isNotEmpty()) {
        imageBitmapUtil.alpha8Bitmap(appContext, DrawingR.drawable.chapter_hdr)
      } else {
        null
      }
      val headerImagePaint = if (hdrBitmap != null) {
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
          color = key.headerColor
        }
      } else {
        null
      }
      val argbHeaderPaint = if (hdrBitmap != null) {
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
          color = key.headerColor
          colorFilter = PorterDuffColorFilter(key.headerColor, PorterDuff.Mode.SRC_IN)
        }
      } else {
        null
      }

      for (index in 0 until lineCount) {
        coroutineContext.ensureActive()
        val lineNumber = index + 1
        val lineTopY = floor((totalHeightInt - layoutLineHeight) / layoutLastLineIndex * index).roundToInt()
        val file = File(pageDirectory, "$lineNumber.png")
        val lineBmp = decodeLineBitmapUnpooled(file, sampleSize)
          ?: run {
            val status = if (file.exists()) {
              "exists: ${file.name} with length ${file.length()}"
            } else {
              "missing"
            }
            throw MissingLineByLineImagesException(
              "Failed to load line $lineNumber for page $page - file $status"
            )
          }

        try {
          dstRect.set(0, lineTopY, totalWidthInt, lineTopY + lineHeightInt)
          val paint = if (lineBmp.config == Bitmap.Config.ALPHA_8) linePaint else argbLinePaint
          canvas.drawBitmap(lineBmp, null, dstRect, paint)
        } finally {
          if (!lineBmp.isRecycled) {
            lineBmp.recycle()
          }
        }

        if (key.showDividers && index > 0) {
          val dividerY = lineTopY.toFloat()
          canvas.drawLine(0f, dividerY, totalWidth, dividerY, dividerPaint)
        }

        if (hdrBitmap != null && !hdrBitmap.isRecycled && details.suraHeaders.isNotEmpty()) {
          val hdrWidth = (totalWidthInt * 1038) / 1080
          val hdrHeight = (hdrWidth * hdrBitmap.height) / hdrBitmap.width
          val hdrPaint = if (hdrBitmap.config == Bitmap.Config.ALPHA_8) {
            headerImagePaint!!
          } else {
            argbHeaderPaint!!
          }
          for (h in 0 until details.suraHeaders.size) {
            val header = details.suraHeaders[h]
            if (header.lineId == index) {
              val xOffset = ((header.centerX * totalWidth) - (hdrWidth / 2)).toInt()
              val yOffset = lineTopY + ((header.centerY * lineHeight) - (hdrHeight / 2)).toInt()
              dstRect.set(xOffset, yOffset, xOffset + hdrWidth, yOffset + hdrHeight)
              canvas.drawBitmap(hdrBitmap, null, dstRect, hdrPaint)
            }
          }
        }
      }

      val markers = details.ayahMarkers
      if (markers.isNotEmpty()) {
        val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 0.003f * totalWidth
          color = key.ringColor
        }
        val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.FILL
          color = key.innerColor
        }
        val markerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          textAlign = Paint.Align.CENTER
          color = key.ayahColor
        }
        val markerDimen = (0.025f * 2 * totalWidth).toInt()
        val radius = markerDimen / 2f
        val markerLineHeight = totalWidth * 174f / 1080f
        val yStep = (totalHeight - markerLineHeight) / 14f
        val smallTextSize = totalWidth * 0.0370f
        val largeTextSize = totalWidth * 0.03f
        val formatter = AYAH_NUMBER_FORMATTER.get()!!

        for (i in 0 until markers.size) {
          val marker = markers[i]
          val xOffset = (marker.centerX * totalWidth) - (markerDimen / 2f)
          val yStart = yStep * marker.lineId
          val yOffset = yStart + (marker.centerY * markerLineHeight) - (markerDimen / 2f)
          val cx = xOffset + radius
          val cy = yOffset + radius

          canvas.drawCircle(cx, cy, radius, markerFillPaint)
          canvas.drawCircle(cx, cy, radius, markerStrokePaint)

          markerTextPaint.textSize = if (marker.ayah > 100) largeTextSize else smallTextSize
          val fm = markerTextPaint.fontMetrics
          val textBaselineY = cy - (fm.ascent + fm.descent) * 0.5f + 3f
          canvas.drawText(formatter.format(marker.ayah), cx, textBaselineY, markerTextPaint)
        }
      }

      coroutineContext.ensureActive()
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val hw = runCatching { swBitmap.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        if (hw != null) {
          releaseScratchBitmap(swBitmap)
          hw
        } else {
          returnedSwBitmap = true
          swBitmap
        }
      } else {
        returnedSwBitmap = true
        swBitmap
      }
    } catch (t: Throwable) {
      if (!returnedSwBitmap) {
        releaseScratchBitmap(swBitmap)
      }
      throw t
    }
  }

  private fun decodeLineBitmapUnpooled(file: File, sampleSize: Int): Bitmap? {
    if (!file.exists() || file.length() == 0L) {
      return null
    }
    val tempStorage = DECODE_TEMP_STORAGE.get()
    val boundsOpts = BitmapFactory.Options().apply {
      inJustDecodeBounds = true
      inPreferredConfig = Bitmap.Config.ALPHA_8
      inTempStorage = tempStorage
    }
    runCatching {
      FileInputStream(file).buffered().use { stream ->
        BitmapFactory.decodeStream(stream, null, boundsOpts)
      }
    }
    val isAlpha8Candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      boundsOpts.outConfig == null || boundsOpts.outConfig == Bitmap.Config.ALPHA_8
    } else {
      true
    }
    val preferredConfig = if (isAlpha8Candidate) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
    val primaryOpts = BitmapFactory.Options().apply {
      inPreferredConfig = preferredConfig
      inMutable = false
      inSampleSize = sampleSize
      inBitmap = null
      inTempStorage = tempStorage
    }
    val decoded = try {
      FileInputStream(file).buffered().use { stream ->
        BitmapFactory.decodeStream(stream, null, primaryOpts)
      }
    } catch (_: Exception) {
      null
    }
    if (decoded != null) {
      return decoded
    }
    val fallbackOpts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inMutable = false
      inSampleSize = sampleSize
      inBitmap = null
      inTempStorage = tempStorage
    }
    return try {
      FileInputStream(file).buffered().use { stream ->
        BitmapFactory.decodeStream(stream, null, fallbackOpts)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun decodeAlpha8Unpooled(filePath: String): Bitmap? {
    val decoded = decodeLineBitmapUnpooled(File(filePath), sampleSize = 1) ?: return null
    return if (decoded.config == Bitmap.Config.ALPHA_8) {
      decoded
    } else {
      decoded.extractAlpha().also { decoded.recycle() }
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
        val decodedSidelines = mutableListOf<Bitmap>()
        try {
          val models = files.mapNotNull {
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

            val bitmap = decodeAlpha8Unpooled(it.toString())
            if (bitmap != null) {
              decodedSidelines.add(bitmap)
              SidelineModel(bitmap.asImageBitmap(), number, actualDirection, bitmap)
            } else {
              crashReporter.log("failed to load sideline image [$it] on page [$page]")
              null
            }
          }.sortedBy { it.targetLine }.toImmutableList()
          synchronized(trackedSidelineBitmaps) {
            trackedSidelineBitmaps.addAll(decodedSidelines)
          }
          models
        } catch (ce: CancellationException) {
          decodedSidelines.forEach { LineBitmapRecycler.scheduleRecycle(it) }
          throw ce
        }
      } else {
        persistentListOf()
      }
    }
  }

  companion object {
    private val COMPOSITING_DISPATCHER = Dispatchers.IO.limitedParallelism(2)

    private val DECODE_TEMP_STORAGE: ThreadLocal<ByteArray> = ThreadLocal.withInitial {
      ByteArray(16 * 1024)
    }

    private val scratchPoolLock = Any()
    private var pooledScratchBitmap: Bitmap? = null
    private val scratchTrimCleaner: () -> Unit = { clearScratchBitmapPool() }

    init {
      BitmapPoolTrimRegistry.register(scratchTrimCleaner)
    }

    @VisibleForTesting
    fun acquireScratchBitmap(width: Int, height: Int): Bitmap {
      val cached = synchronized(scratchPoolLock) {
        val current = pooledScratchBitmap
        pooledScratchBitmap = null
        current
      }
      if (cached != null && !cached.isRecycled) {
        if (cached.width == width && cached.height == height && cached.isMutable) {
          return cached
        }
        cached.recycle()
      }
      return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    @VisibleForTesting
    fun releaseScratchBitmap(bitmap: Bitmap?) {
      if (bitmap == null || bitmap.isRecycled) return
      val evicted: Bitmap?
      synchronized(scratchPoolLock) {
        val current = pooledScratchBitmap
        if (current === bitmap) {
          return
        }
        evicted = current
        pooledScratchBitmap = bitmap
      }
      if (evicted != null && !evicted.isRecycled) {
        evicted.recycle()
      }
    }

    @VisibleForTesting
    fun clearScratchBitmapPool() {
      val toRecycle = synchronized(scratchPoolLock) {
        val current = pooledScratchBitmap
        pooledScratchBitmap = null
        current
      }
      if (toRecycle != null && !toRecycle.isRecycled) {
        toRecycle.recycle()
      }
    }

    @VisibleForTesting
    fun hasPooledScratchBitmapForTest(): Boolean {
      return synchronized(scratchPoolLock) {
        val bmp = pooledScratchBitmap
        bmp != null && !bmp.isRecycled
      }
    }

    internal fun decodeTempStorageForTest(): ByteArray = DECODE_TEMP_STORAGE.get()!!

    val AYAH_NUMBER_FORMATTER: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
      NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar-EG"))
    }
  }
}
