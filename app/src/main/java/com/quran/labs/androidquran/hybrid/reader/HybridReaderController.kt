package com.quran.labs.androidquran.hybrid.reader

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.feature.pagetheme.PageTheme
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.state.PageColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HybridReaderController(
  private val appContext: Context,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val quranSettings: QuranSettings,
  private val dailyWirdRepository: DailyWirdRepository,
  private val audioStatusRepository: AudioStatusRepository,
  private val currentQariManager: CurrentQariManager,
  private val downloadInfoStreams: DownloadInfoStreams,
  private val timingRepository: TimingRepository,
  private val bookmarksDao: BookmarksDao,
  private val tafsirLookupHelper: TafsirLookupHelper,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val defaultPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
) {

  private val _uiState = MutableStateFlow(
    HybridReaderUiState(
      appearanceState = readAppearanceState()
    )
  )
  val uiState: StateFlow<HybridReaderUiState> = _uiState.asStateFlow()

  var pendingPlaybackSpeed: Float = 1.0f
    private set
  var pendingRepeatCount: Int = 0
    private set

  private var preferredTafsirFilename: String? = null
  private var tafsirJob: Job? = null
  private var timingJob: Job? = null
  private var bookmarkCheckJob: Job? = null
  private var pageSettleJob: Job? = null
  private var lastPersistedPage: Int = -1
  private var lastTimingKey: Triple<String, Int, Int>? = null

  fun startObserving() {
    currentQariManager.flow()
      .onEach { qari ->
        val name = runCatching { appContext.getString(qari.nameResource) }.getOrDefault("")
        _uiState.update { state ->
          state.copy(
            qariShortName = name,
            qariFullName = name
          )
        }
      }
      .launchIn(scope)

    audioStatusRepository.audioPlaybackFlow
      .onEach { status -> handleAudioStatus(status) }
      .launchIn(scope)

    downloadInfoStreams.downloadInfoStream()
      .onEach { info -> handleDownloadInfo(info) }
      .launchIn(scope)

    bookmarksDao.changes
      .onEach { refreshSelectedAyahBookmarkState() }
      .launchIn(scope)
  }

  private fun handleAudioStatus(status: AudioStatus) {
    when (status) {
      is AudioStatus.Stopped -> {
        lastTimingKey = null
        timingJob?.cancel()
        _uiState.update { state ->
          state.copy(
            playbackStatus = null,
            playbackAyah = null,
            playbackStart = null,
            playbackEnd = null,
            playbackSpeed = pendingPlaybackSpeed,
            repeatCount = pendingRepeatCount,
            isGaplessAudio = false,
            ayahDurationMs = 0L
          )
        }
      }
      is AudioStatus.Playback -> {
        val currentAyah = status.currentAyah
        val req = status.audioRequest
        val gaplessDb = req.audioPathInfo.gaplessDatabase
        val isGapless = !gaplessDb.isNullOrBlank()
        pendingPlaybackSpeed = req.playbackSpeed
        pendingRepeatCount = req.repeatInfo

        val currentQari = currentQariManager.currentQari()
        val keepSelectedQariName = status.playbackStatus == PlaybackStatus.PAUSED &&
          req.qari.id != currentQari.id
        val resolvedQariName = if (keepSelectedQariName) {
          runCatching { appContext.getString(currentQari.nameResource) }.getOrDefault("")
        } else {
          req.qari.name
        }

        _uiState.update { state ->
          state.copy(
            qariShortName = resolvedQariName.ifBlank { state.qariShortName },
            qariFullName = resolvedQariName.ifBlank { state.qariFullName },
            playbackStatus = status.playbackStatus,
            playbackAyah = currentAyah,
            playbackStart = req.start,
            playbackEnd = req.end,
            playbackSpeed = req.playbackSpeed,
            repeatCount = req.repeatInfo,
            isGaplessAudio = isGapless,
            downloadProgress = null,
            showNetworkPrompt = false
          )
        }

        if (isGapless && gaplessDb != null) {
          resolveAyahDuration(gaplessDb, currentAyah.sura, currentAyah.ayah)
        } else {
          lastTimingKey = null
          timingJob?.cancel()
          _uiState.update { it.copy(ayahDurationMs = 0L) }
        }

        if (_uiState.value.isStudyDrawerOpen &&
          _uiState.value.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR &&
          _uiState.value.selectedStart == null
        ) {
          refreshTafsirForAyah(currentAyah.sura, currentAyah.ayah)
        }
      }
    }
  }

  private fun resolveAyahDuration(gaplessDb: String, sura: Int, ayah: Int) {
    val key = Triple(gaplessDb, sura, ayah)
    if (key == lastTimingKey && _uiState.value.ayahDurationMs > 0L) return
    lastTimingKey = key
    timingJob?.cancel()
    timingJob = scope.launch(ioDispatcher) {
      val timings = runCatching { timingRepository.timings(gaplessDb, sura) }.getOrNull()
      val duration = if (timings != null) {
        val startMs = timings.ayahTimings.get(ayah, -1L)
        val totalAyahs = quranInfo.getNumberOfAyahs(sura)
        val nextKey = if (ayah < totalAyahs) ayah + 1 else 999
        val endMs = timings.ayahTimings.get(nextKey, -1L)
        if (startMs >= 0L && endMs > startMs) endMs - startMs else 0L
      } else {
        0L
      }
      _uiState.update { it.copy(ayahDurationMs = duration) }
    }
  }

  private fun handleDownloadInfo(info: DownloadInfo) {
    when (info) {
      is DownloadInfo.FileDownloadProgress -> {
        _uiState.update { it.copy(downloadProgress = info.progress, showNetworkPrompt = false) }
      }
      is DownloadInfo.DownloadRequested, is DownloadInfo.DownloadEvent -> {
        _uiState.update { it.copy(downloadProgress = -1, showNetworkPrompt = false) }
      }
      DownloadInfo.RequestDownloadNetworkPermission -> {
        _uiState.update { it.copy(downloadProgress = null, showNetworkPrompt = true) }
      }
      is DownloadInfo.DownloadBatchSuccess, is DownloadInfo.DownloadBatchError -> {
        _uiState.update { it.copy(downloadProgress = null, showNetworkPrompt = false) }
      }
    }
  }

  fun updateCurrentPage(page: Int) {
    if (!quranInfo.isValidPage(page)) return
    val sura = quranDisplayData.safelyGetSuraOnPage(page)
    val suraName = runCatching {
      quranDisplayData.getSuraNameFromPage(appContext, page, false)
    }.getOrDefault("")
    val juz = quranInfo.getJuzForDisplayFromPage(page)
    val firstAyah = quranInfo.getFirstAyahOnPage(page).coerceAtLeast(1)

    _uiState.update { state ->
      state.copy(
        currentPage = page,
        suraNumber = sura,
        suraName = suraName,
        juzNumber = juz,
        firstAyahOnPage = firstAyah
      )
    }

    if (_uiState.value.isStudyDrawerOpen &&
      _uiState.value.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR &&
      _uiState.value.selectedStart == null &&
      _uiState.value.playbackAyah == null
    ) {
      refreshTafsirForAyah(sura, firstAyah)
    }
  }

  fun recordSettledPage(page: Int) {
    if (!quranInfo.isValidPage(page) || page == lastPersistedPage) return
    lastPersistedPage = page
    val sura = quranDisplayData.safelyGetSuraOnPage(page)
    val suraName = runCatching {
      val wantPrefix = appContext.resources.getBoolean(com.quran.labs.androidquran.R.bool.show_surat_prefix)
      quranDisplayData.getSuraName(appContext, sura, wantPrefix = wantPrefix)
    }.getOrDefault("")
    val firstAyah = quranInfo.getFirstAyahOnPage(page)
    val juz = quranInfo.getJuzForDisplayFromPage(page)

    pageSettleJob?.cancel()
    pageSettleJob = scope.launch(ioDispatcher) {
      dailyWirdRepository.recordPageReadAndLocation(
        page = page,
        suraName = suraName,
        ayah = firstAyah,
        juz = juz
      )
    }
  }

  fun setChromeVisible(visible: Boolean) {
    _uiState.update { it.copy(isChromeVisible = visible) }
  }

  fun setCurrentPageBookmarked(bookmarked: Boolean) {
    _uiState.update { it.copy(isCurrentPageBookmarked = bookmarked) }
  }

  fun onAyahSelectionChanged(
    start: SuraAyah?,
    end: SuraAyah?,
    bounds: AyahOverlayBounds?
  ) {
    val selectedSuraName = if (start != null) {
      runCatching {
        val wantPrefix = appContext.resources.getBoolean(R.bool.show_surat_prefix)
        quranDisplayData.getSuraName(appContext, start.sura, wantPrefix)
      }.getOrDefault("")
    } else {
      ""
    }
    _uiState.update { state ->
      state.copy(
        selectedStart = start,
        selectedEnd = end,
        selectedSuraName = selectedSuraName,
        ayahOverlayBounds = if (start != null) bounds else null,
        isSelectedAyahBookmarked = if (start != null) state.isSelectedAyahBookmarked else false
      )
    }
    refreshSelectedAyahBookmarkState()
    if (start != null &&
      _uiState.value.isStudyDrawerOpen &&
      _uiState.value.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR
    ) {
      refreshTafsirForAyah(start.sura, start.ayah)
    }
  }

  fun updateAyahOverlayBounds(bounds: AyahOverlayBounds?) {
    val current = _uiState.value
    if (current.selectedStart == null || current.ayahOverlayBounds == bounds) return
    _uiState.update { it.copy(ayahOverlayBounds = bounds) }
  }

  fun refreshSelectedAyahBookmarkState() {
    val start = _uiState.value.selectedStart
    bookmarkCheckJob?.cancel()
    if (start == null) {
      _uiState.update { it.copy(isSelectedAyahBookmarked = false) }
      return
    }
    bookmarkCheckJob = scope.launch(ioDispatcher) {
      val bookmarked = runCatching { bookmarksDao.isSuraAyahBookmarked(start) }.getOrDefault(false)
      _uiState.update { state ->
        if (state.selectedStart == start) {
          state.copy(isSelectedAyahBookmarked = bookmarked)
        } else {
          state
        }
      }
    }
  }

  fun setSelectedAyahBookmarked(suraAyah: SuraAyah, bookmarked: Boolean) {
    _uiState.update { state ->
      if (state.selectedStart == suraAyah) {
        state.copy(isSelectedAyahBookmarked = bookmarked)
      } else {
        state
      }
    }
  }

  fun openStudyDrawer(tab: HybridReaderContract.StudyTab = _uiState.value.selectedStudyTab) {
    if (tab == HybridReaderContract.StudyTab.APPEARANCE) {
      openAppearanceSheet()
      return
    }
    _uiState.update { state ->
      state.copy(
        isStudyDrawerOpen = true,
        isAppearanceSheetOpen = false,
        selectedStudyTab = tab,
        isChromeVisible = true
      )
    }
    if (tab == HybridReaderContract.StudyTab.TAFSIR) {
      refreshCurrentStudyAyahTafsir()
    }
  }

  fun openAppearanceSheet() {
    _uiState.update { state ->
      state.copy(
        isAppearanceSheetOpen = true,
        isStudyDrawerOpen = false,
        isChromeVisible = true
      )
    }
  }

  fun closeAppearanceSheet() {
    _uiState.update { it.copy(isAppearanceSheetOpen = false) }
  }

  fun preloadStudyTafsirIfNeeded() {
    val state = _uiState.value
    if (state.isStudyDrawerOpen && state.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR) {
      refreshCurrentStudyAyahTafsir()
    }
  }

  fun closeStudyDrawer() {
    _uiState.update { it.copy(isStudyDrawerOpen = false) }
  }

  fun selectStudyTab(tab: HybridReaderContract.StudyTab) {
    if (tab == HybridReaderContract.StudyTab.APPEARANCE) {
      openAppearanceSheet()
      return
    }
    _uiState.update { it.copy(selectedStudyTab = tab) }
    if (tab == HybridReaderContract.StudyTab.TAFSIR) {
      refreshCurrentStudyAyahTafsir()
    }
  }

  fun selectTafsirSource(filename: String) {
    preferredTafsirFilename = filename
    refreshCurrentStudyAyahTafsir(force = true)
  }

  fun refreshTafsirAfterResume() {
    tafsirLookupHelper.invalidateCache()
    if (_uiState.value.isStudyDrawerOpen &&
      _uiState.value.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR
    ) {
      refreshCurrentStudyAyahTafsir(force = true)
    } else {
      _uiState.update { state ->
        state.copy(
          tafsirState = state.tafsirState.copy(loadState = TafsirLoadState.IDLE)
        )
      }
    }
  }

  fun refreshCurrentStudyAyahTafsir(force: Boolean = false) {
    val target = resolveTargetStudyAyah()
    refreshTafsirForAyah(target.sura, target.ayah, force = force)
  }

  fun resolveTargetStudyAyah(): SuraAyah {
    val state = _uiState.value
    state.selectedStart?.let { return it }
    state.playbackAyah?.let { return it }
    val page = state.currentPage
    val sura = quranDisplayData.safelyGetSuraOnPage(page)
    val ayah = quranInfo.getFirstAyahOnPage(page)
    return SuraAyah(sura, ayah)
  }

  fun adjacentAyah(current: SuraAyah, next: Boolean): SuraAyah? {
    return if (next) {
      val total = quranInfo.getNumberOfAyahs(current.sura)
      when {
        current.ayah + 1 <= total -> SuraAyah(current.sura, current.ayah + 1)
        current.sura < Constants.SURA_LAST -> SuraAyah(current.sura + 1, 1)
        else -> null
      }
    } else {
      when {
        current.ayah > 1 -> SuraAyah(current.sura, current.ayah - 1)
        current.sura > Constants.SURA_FIRST -> {
          val prevSura = current.sura - 1
          SuraAyah(prevSura, quranInfo.getNumberOfAyahs(prevSura))
        }
        else -> null
      }
    }
  }

  private fun refreshTafsirForAyah(sura: Int, ayah: Int, force: Boolean = false) {
    val current = _uiState.value.tafsirState
    val sameSource = preferredTafsirFilename == null || current.selectedFilename == preferredTafsirFilename
    if (!force &&
      current.sura == sura &&
      current.ayah == ayah &&
      sameSource &&
      (current.loadState == TafsirLoadState.LOADED ||
        (current.loadState == TafsirLoadState.LOADING && tafsirJob?.isActive == true))
    ) {
      return
    }
    _uiState.update { state ->
      state.copy(
        tafsirState = state.tafsirState.copy(
          loadState = TafsirLoadState.LOADING,
          sura = sura,
          ayah = ayah
        )
      )
    }

    tafsirJob?.cancel()
    tafsirJob = scope.launch {
      val (suraName, activeFiles) = withContext(ioDispatcher) {
        val name = runCatching {
          quranDisplayData.getSuraName(appContext, sura, wantPrefix = true)
        }.getOrDefault("")
        val files = runCatching {
          quranSettings.activeTranslations.filterNotNull().toSet()
        }.getOrDefault(emptySet())
        name to files
      }
      val loaded = tafsirLookupHelper.loadTafsirForAyah(
        sura = sura,
        ayah = ayah,
        suraName = suraName,
        preferredFilename = preferredTafsirFilename,
        activeFilenames = activeFiles
      )
      _uiState.update { it.copy(tafsirState = loaded) }
    }
  }

  fun updatePendingAudioOptions(speed: Float? = null, repeat: Int? = null) {
    if (speed != null) pendingPlaybackSpeed = speed
    if (repeat != null) pendingRepeatCount = repeat
    _uiState.update { state ->
      state.copy(
        playbackSpeed = speed ?: state.playbackSpeed,
        repeatCount = repeat ?: state.repeatCount
      )
    }
  }

  fun syncAppearanceState() {
    _uiState.update { it.copy(appearanceState = readAppearanceState()) }
  }

  fun selectAppThemeStyle(style: AppThemeStyle) {
    quranSettings.appThemeStyle = style.storageKey
    AppThemeStyle.select(style)
    syncAppearanceState()
  }

  fun selectAppThemeMode(mode: String) {
    defaultPrefs.edit().putString(Constants.PREF_APP_THEME, mode).apply()
    quranSettings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    ThemeUtil.setTheme(mode)
    syncAppearanceState()
  }

  fun setOledDarkMode(enabled: Boolean) {
    quranSettings.isOledDarkMode = enabled
    AppThemeStyle.setOledDarkMode(enabled)
    syncAppearanceState()
  }

  fun selectPageColor(color: PageColor) {
    val currentIsNight = quranSettings.isNightMode
    val currentMatchApp = quranSettings.pageAppearance == PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    if (color == PageColor.Dark) {
      quranSettings.pageAppearance = if (currentMatchApp && currentIsNight) {
        PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
      } else {
        PageThemeConstants.PAGE_APPEARANCE_DARK
      }
    } else {
      val pageTheme = if (color == PageColor.Sepia) PageTheme.Sepia else PageTheme.Light
      quranSettings.pageTheme = pageTheme.storageKey
      quranSettings.pageAppearance = if (currentMatchApp && !currentIsNight) {
        PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
      } else {
        PageThemeConstants.PAGE_APPEARANCE_LIGHT
      }
    }
    syncAppearanceState()
  }

  fun setMatchAppTheme(matchAppTheme: Boolean) {
    val currentIsNight = quranSettings.isNightMode
    quranSettings.pageAppearance = when {
      matchAppTheme -> PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
      currentIsNight -> PageThemeConstants.PAGE_APPEARANCE_DARK
      else -> PageThemeConstants.PAGE_APPEARANCE_LIGHT
    }
    syncAppearanceState()
  }

  fun setNightTextBrightness(brightness: Int) {
    val clamped = brightness.coerceIn(PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS, 255)
    quranSettings.nightModeTextBrightness = clamped
    syncAppearanceState()
  }

  fun setNightBackgroundBrightness(brightness: Int) {
    val clamped = brightness.coerceIn(0, PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS)
    quranSettings.nightModeBackgroundBrightness = clamped
    syncAppearanceState()
  }

  private fun readAppearanceState(): ReaderAppearanceState {
    val style = AppThemeStyle.fromPreference(quranSettings.appThemeStyle)
    val appMode = runCatching { quranSettings.currentTheme() }.getOrDefault(Constants.THEME_DEFAULT)
    val isOledDarkMode = runCatching { quranSettings.isOledDarkMode }.getOrDefault(false)
    val pageTheme = runCatching { quranSettings.pageTheme }.getOrDefault(PageThemeConstants.PAGE_THEME_LIGHT)
    val pageAppearance = runCatching { quranSettings.pageAppearance }.getOrDefault(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
    val isNightPage = runCatching { quranSettings.isNightMode }.getOrDefault(false)
    val textBright = runCatching { quranSettings.nightModeTextBrightness }
      .getOrDefault(Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS)
    val bgBright = runCatching { quranSettings.nightModeBackgroundBrightness }
      .getOrDefault(Constants.DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS)
      .coerceIn(0, PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS)

    return ReaderAppearanceState(
      appThemeStyle = style,
      appThemeMode = appMode,
      isOledDarkMode = isOledDarkMode,
      pageTheme = pageTheme,
      pageAppearance = pageAppearance,
      isNightPage = isNightPage,
      textBrightness = textBright,
      backgroundBrightness = bgBright
    )
  }

  fun restoreUiState(
    isDrawerOpen: Boolean,
    studyTab: HybridReaderContract.StudyTab,
    isAppearanceSheetOpen: Boolean = false
  ) {
    val normalizedTab = if (studyTab == HybridReaderContract.StudyTab.APPEARANCE) {
      HybridReaderContract.StudyTab.RECITATION
    } else {
      studyTab
    }
    if (isAppearanceSheetOpen || (isDrawerOpen && studyTab == HybridReaderContract.StudyTab.APPEARANCE)) {
      _uiState.update { state ->
        state.copy(
          isStudyDrawerOpen = false,
          isAppearanceSheetOpen = true,
          selectedStudyTab = normalizedTab,
          isChromeVisible = true
        )
      }
      return
    }
    _uiState.update { state ->
      state.copy(
        isStudyDrawerOpen = isDrawerOpen,
        isAppearanceSheetOpen = false,
        selectedStudyTab = normalizedTab,
        isChromeVisible = if (isDrawerOpen) true else state.isChromeVisible
      )
    }
    if (isDrawerOpen && normalizedTab == HybridReaderContract.StudyTab.TAFSIR) {
      refreshCurrentStudyAyahTafsir()
    }
  }
}
