package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import androidx.preference.PreferenceManager
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.di.QuranActivityScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.di.qualifier.ActivityContext
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@QuranActivityScope
class HybridHubStateHolder internal constructor(
  private val context: Context,
  private val dailyWirdRepository: DailyWirdRepository,
  private val recentPagesDao: RecentPagesDao,
  private val bookmarksDao: Provider<BookmarksDao>,
  private val readingBookmarksDao: Provider<ReadingBookmarksDao>,
  private val highlightsDao: Provider<HighlightsDao>,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val settings: QuranSettings,
  private val arabicDatabaseUtils: Provider<ArabicDatabaseUtils>,
  private val indexDataSource: HybridIndexDataSource,
  private val ioDispatcher: CoroutineDispatcher,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

  @Inject
  constructor(
    @ActivityContext context: Context,
    dailyWirdRepository: DailyWirdRepository,
    recentPagesDao: RecentPagesDao,
    bookmarksDao: Provider<BookmarksDao>,
    readingBookmarksDao: Provider<ReadingBookmarksDao>,
    highlightsDao: Provider<HighlightsDao>,
    quranInfo: QuranInfo,
    quranDisplayData: QuranDisplayData,
    settings: QuranSettings,
    arabicDatabaseUtils: Provider<ArabicDatabaseUtils>,
    indexDataSource: HybridIndexDataSource
  ) : this(
    context = context,
    dailyWirdRepository = dailyWirdRepository,
    recentPagesDao = recentPagesDao,
    bookmarksDao = bookmarksDao,
    readingBookmarksDao = readingBookmarksDao,
    highlightsDao = highlightsDao,
    quranInfo = quranInfo,
    quranDisplayData = quranDisplayData,
    settings = settings,
    arabicDatabaseUtils = arabicDatabaseUtils,
    indexDataSource = indexDataSource,
    ioDispatcher = Dispatchers.IO,
    computeDispatcher = Dispatchers.Default
  )

  private val wantSuraPrefix: Boolean
    get() = context.resources.getBoolean(R.bool.show_surat_prefix)

  private val _uiState: MutableStateFlow<HybridHubUiState> = MutableStateFlow(createInitialState())
  val uiState: StateFlow<HybridHubUiState> = _uiState.asStateFlow()

  private var observingScope: CoroutineScope? = null
  private var bookmarksCollectionJob: Job? = null
  private var quartersHydratedFromDb = false
  private var observationStarted = false

  private fun createInitialState(): HybridHubUiState {
    val initialWird = dailyWirdRepository.stateFlow.value
    val prewarmedSuras = indexDataSource.getCachedSurasOrNull(context)
    return HybridHubUiState(
      selectedTab = HybridNavTab.INDEX,
      indexSegment = HubIndexSegment.SURAS,
      themeStyle = AppThemeStyle.fromPreference(settings.appThemeStyle),
      appTheme = settings.currentTheme(),
      dailyWird = initialWird,
      fallbackLastSuraName = suraNameForPage(initialWird.lastPage),
      suras = prewarmedSuras ?: indexDataSource.buildSurasRange(context, 1, INITIAL_VISIBLE_SURAS),
      juzs = emptyList(),
      hizbs = emptyList(),
      bookmarks = HubBookmarksUiState()
    )
  }

  fun startObserving(scope: CoroutineScope, latestPageFlow: Flow<Int>) {
    observingScope = scope
    if (observationStarted) return
    observationStarted = true

    if (_uiState.value.suras.size < TOTAL_SURAS_COUNT) {
      scope.launch(computeDispatcher) {
        val allSuras = indexDataSource.buildSuras(context)
        _uiState.update { state ->
          if (state.suras === allSuras) state else state.copy(suras = allSuras)
        }
      }
    }

    scope.launch {
      dailyWirdRepository.stateFlow.collect { wird ->
        _uiState.update { state ->
          state.copy(
            dailyWird = wird,
            fallbackLastSuraName = suraNameForPage(wird.lastPage)
          )
        }
      }
    }

    scope.launch {
      latestPageFlow
        .filter { page -> page in 1..quranInfo.numberOfPages }
        .distinctUntilChanged()
        .collect { page ->
          val sura = quranInfo.getSuraOnPage(page)
          val ayah = quranInfo.getFirstAyahOnPage(page)
          val juz = quranInfo.getJuzForDisplayFromPage(page)
          val suraName = quranDisplayData.getSuraName(
            context = context,
            sura = sura,
            wantPrefix = wantSuraPrefix,
            wantTranslation = false
          )
          dailyWirdRepository.updateLastLocation(
            suraName = suraName,
            ayah = ayah,
            page = page,
            juz = juz
          )
        }
    }
  }

  fun selectNavTab(tab: HybridNavTab) {
    if (tab == HybridNavTab.READ) return
    _uiState.update { it.copy(selectedTab = tab) }
    if (tab == HybridNavTab.BOOKMARKS) {
      ensureBookmarksObserved()
    }
  }

  fun selectIndexSegment(segment: HubIndexSegment) {
    var shouldHydrateDb = false
    _uiState.update { state ->
      when {
        segment == HubIndexSegment.JUZ && state.juzs.isEmpty() -> {
          shouldHydrateDb = QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE && !quartersHydratedFromDb
          val quarters = indexDataSource.loadResourceQuartersOrEmpty(context)
          state.copy(
            indexSegment = segment,
            juzs = indexDataSource.buildJuzs(context, quarters)
          )
        }
        segment == HubIndexSegment.HIZB && state.hizbs.isEmpty() -> {
          shouldHydrateDb = QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE && !quartersHydratedFromDb
          val quarters = indexDataSource.loadResourceQuartersOrEmpty(context)
          state.copy(
            indexSegment = segment,
            hizbs = indexDataSource.buildHizbs(context, quarters)
          )
        }
        else -> state.copy(indexSegment = segment)
      }
    }
    if (shouldHydrateDb) {
      quartersHydratedFromDb = true
      observingScope?.launch(ioDispatcher) {
        val dbQuarters = indexDataSource.loadQuarterTexts(context)
        if (dbQuarters.isNotEmpty()) {
          _uiState.update { state ->
            state.copy(
              juzs = if (state.juzs.isNotEmpty()) indexDataSource.buildJuzs(context, dbQuarters) else state.juzs,
              hizbs = if (state.hizbs.isNotEmpty()) indexDataSource.buildHizbs(context, dbQuarters) else state.hizbs
            )
          }
        }
      }
    }
  }

  fun setDailyWirdGoal(goalPages: Int) {
    val scope = observingScope ?: return
    scope.launch {
      dailyWirdRepository.setGoalPages(goalPages)
    }
  }

  fun refreshDailyWirdForNewDay() {
    val scope = observingScope ?: return
    scope.launch {
      dailyWirdRepository.refreshIfNewDay()
    }
  }

  fun syncThemeFromSettings() {
    _uiState.update {
      it.copy(
        themeStyle = AppThemeStyle.fromPreference(settings.appThemeStyle),
        appTheme = settings.currentTheme()
      )
    }
  }

  fun updateThemeMode(mode: String) {
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit()
      .putString(Constants.PREF_APP_THEME, mode)
      .apply()
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    ThemeUtil.setTheme(mode)
    _uiState.update { it.copy(appTheme = mode) }
  }

  fun updateThemeStyle(style: AppThemeStyle) {
    settings.appThemeStyle = style.storageKey
    ThemeUtil.applyThemeStyle(style)
    _uiState.update { it.copy(themeStyle = style) }
  }

  suspend fun toggleAyahBookmark(sura: Int, ayah: Int): Boolean {
    return withContext(ioDispatcher) {
      val suraAyah = SuraAyah(sura, ayah)
      val dao = bookmarksDao()
      if (dao.isSuraAyahBookmarked(suraAyah)) {
        dao.deleteAyahBookmark(suraAyah)
      } else {
        val tags = dao.tags()
        val defaultId = tags.firstOrNull { it.isDefault }?.id
          ?: tags.firstOrNull { !it.isSystem }?.id
          ?: dao.addCollection(DEFAULT_COLLECTION_NAME).id
        dao.replaceAyahBookmarkCollections(suraAyah, setOf(defaultId))
      }
    }
  }

  fun removeAyahBookmark(sura: Int, ayah: Int) {
    val scope = observingScope ?: return
    scope.launch(ioDispatcher) {
      bookmarksDao().deleteAyahBookmark(SuraAyah(sura, ayah))
    }
  }

  fun clearReadingBookmark(slot: ReadingBookmarkType) {
    val scope = observingScope ?: return
    scope.launch(ioDispatcher) {
      readingBookmarksDao().clearReadingBookmark(slot)
    }
  }

  fun removeRecentPage(page: Int) {
    val scope = observingScope ?: return
    scope.launch(ioDispatcher) {
      recentPagesDao.removeRecentsForPage(page)
    }
  }

  fun clearAllRecentPages() {
    val scope = observingScope ?: return
    scope.launch(ioDispatcher) {
      recentPagesDao.removeRecentPages()
    }
  }

  fun clearHighlight(sura: Int, ayah: Int) {
    val scope = observingScope ?: return
    scope.launch(ioDispatcher) {
      highlightsDao().clearHighlight(SuraAyah(sura, ayah))
    }
  }

  fun releaseTransientBookmarksOnStop() {
    if (_uiState.value.selectedTab != HybridNavTab.BOOKMARKS) {
      bookmarksCollectionJob?.cancel()
      bookmarksCollectionJob = null
      if (_uiState.value.bookmarks != HubBookmarksUiState()) {
        _uiState.update { it.copy(bookmarks = HubBookmarksUiState()) }
      }
    }
  }

  internal fun ensureBookmarksObserved() {
    if (bookmarksCollectionJob != null) return
    val scope = observingScope ?: return
    bookmarksCollectionJob = scope.launch {
      combine(
        readingBookmarksDao().readingBookmarksFlow(),
        recentPagesDao.recentPagesFlow(),
        bookmarksDao().bookmarksFlow(BookmarkSortOrder.SORT_DATE_ADDED),
        highlightsDao().highlightsFlow()
      ) { readingBookmarks, recentPages, ayahBookmarks, highlights ->
        val suraAyahsToHydrate = buildSet {
          ayahBookmarks.forEach { bookmark ->
            val sura = bookmark.sura
            val ayah = bookmark.ayah
            if (sura != null && ayah != null) {
              add(SuraAyah(sura, ayah))
            }
          }
          highlights.forEach { highlight ->
            add(highlight.suraAyah)
          }
        }
        val ayahTexts = if (suraAyahsToHydrate.isEmpty()) {
          emptyMap()
        } else {
          withContext(ioDispatcher) {
            runCatching { arabicDatabaseUtils().getAyahTextForSuraAyahs(suraAyahsToHydrate) }
              .getOrDefault(emptyMap())
          }
        }

        val mappedReading = readingBookmarks.mapNotNull { rb ->
          when (rb) {
            is EmptyReadingBookmark -> null
            is PageReadingBookmark -> {
              val page = rb.page.coerceIn(1, quranInfo.numberOfPages)
              HubReadingBookmarkItem(
                slot = rb.slot,
                page = page,
                sura = null,
                ayah = null,
                suraName = suraNameForPage(page),
                juz = quranInfo.getJuzForDisplayFromPage(page),
                customName = rb.name
              )
            }
            is AyahReadingBookmark -> {
              val page = quranInfo.getPageFromSuraAyah(rb.sura, rb.ayah)
                .coerceIn(1, quranInfo.numberOfPages)
              HubReadingBookmarkItem(
                slot = rb.slot,
                page = page,
                sura = rb.sura,
                ayah = rb.ayah,
                suraName = suraNameForSura(rb.sura),
                juz = quranInfo.getJuzForDisplayFromPage(page),
                customName = rb.name
              )
            }
          }
        }

        val mappedRecents = recentPages.map { recent ->
          val page = recent.page.coerceIn(1, quranInfo.numberOfPages)
          HubRecentPageItem(
            page = page,
            suraName = suraNameForPage(page),
            juz = quranInfo.getJuzForDisplayFromPage(page)
          )
        }

        val mappedBookmarks = ayahBookmarks.mapNotNull { bookmark ->
          val sura = bookmark.sura
          val ayah = bookmark.ayah
          if (sura == null || ayah == null) {
            null
          } else {
            val page = bookmark.page.coerceIn(1, quranInfo.numberOfPages)
            HubAyahBookmarkItem(
              id = bookmark.id,
              sura = sura,
              ayah = ayah,
              page = page,
              suraName = suraNameForSura(sura),
              juz = quranInfo.getJuzForDisplayFromPage(page),
              ayahText = bookmark.ayahText ?: ayahTexts[SuraAyah(sura, ayah)]
            )
          }
        }

        val mappedHighlights = highlights.map { highlight ->
          val sura = highlight.suraAyah.sura
          val ayah = highlight.suraAyah.ayah
          val page = quranInfo.getPageFromSuraAyah(sura, ayah)
            .coerceIn(1, quranInfo.numberOfPages)
          HubHighlightItem(
            sura = sura,
            ayah = ayah,
            page = page,
            suraName = suraNameForSura(sura),
            juz = quranInfo.getJuzForDisplayFromPage(page),
            ayahText = ayahTexts[highlight.suraAyah],
            color = highlight.color
          )
        }

        HubBookmarksUiState(
          readingBookmarks = mappedReading,
          recentPages = mappedRecents,
          ayahBookmarks = mappedBookmarks,
          highlights = mappedHighlights,
          isLoaded = true
        )
      }.collect { bookmarksState ->
        _uiState.update { it.copy(bookmarks = bookmarksState) }
      }
    }
  }

  private fun suraNameForPage(page: Int): String {
    val safePage = page.coerceIn(1, quranInfo.numberOfPages)
    val sura = quranInfo.getSuraOnPage(safePage)
    return suraNameForSura(sura)
  }

  private fun suraNameForSura(sura: Int): String {
    return quranDisplayData.getSuraName(
      context = context,
      sura = sura,
      wantPrefix = wantSuraPrefix,
      wantTranslation = false
    )
  }

  companion object {
    private const val DEFAULT_COLLECTION_NAME = "Favorites"
    private const val INITIAL_VISIBLE_SURAS = 12
    private const val TOTAL_SURAS_COUNT = 114
  }
}
