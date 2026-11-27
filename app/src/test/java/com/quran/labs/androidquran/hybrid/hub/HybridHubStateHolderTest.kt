package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridHubStateHolderTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo
  private lateinit var settings: QuranSettings
  private lateinit var dailyWirdRepository: DailyWirdRepository
  private lateinit var recentPagesDao: FakeRecentPagesDao
  private lateinit var bookmarksDao: FakeBookmarksDao
  private lateinit var readingBookmarksDao: FakeReadingBookmarksDao
  private lateinit var highlightsDao: FakeHighlightsDao
  private lateinit var stateHolder: HybridHubStateHolder
  private lateinit var latestPageFlow: MutableStateFlow<Int>

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
    settings = QuranSettings.getInstance(context)
    settings.appThemeStyle = AppThemeStyle.DEFAULT.storageKey
    settings.isOledDarkMode = false
    AppThemeStyle.setOledDarkMode(false)
    settings.pageType = "madani"

    val wirdPrefs = context.getSharedPreferences("test_hub_wird_prefs", Context.MODE_PRIVATE)
    wirdPrefs.edit().clear().commit()
    dailyWirdRepository = DailyWirdRepository(wirdPrefs) { 20_100L }

    recentPagesDao = FakeRecentPagesDao()
    bookmarksDao = FakeBookmarksDao { sa -> quranInfo.getPageFromSuraAyah(sa.sura, sa.ayah) }
    readingBookmarksDao = FakeReadingBookmarksDao()
    highlightsDao = FakeHighlightsDao()
    latestPageFlow = MutableStateFlow(Constants.NO_PAGE)

    val displayData = QuranDisplayData(quranInfo)
    val arabicDb = ArabicDatabaseUtils(context, quranInfo, FakeQuranFileUtils.create(context))
    val indexDataSource = HybridIndexDataSource(
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      juzListPresenter = Provider { JuzListPresenter(quranInfo, arabicDb) }
    )

    stateHolder = HybridHubStateHolder(
      context = context,
      dailyWirdRepository = dailyWirdRepository,
      recentPagesDao = recentPagesDao,
      bookmarksDao = Provider { bookmarksDao },
      readingBookmarksDao = Provider { readingBookmarksDao },
      highlightsDao = Provider { highlightsDao },
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      arabicDatabaseUtils = Provider { arabicDb },
      indexDataSource = indexDataSource,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )
  }

  @Test
  fun `initial state builds visible sura slice lazily and populates full lists on startObserving or segment switch`() = runTest(UnconfinedTestDispatcher()) {
    val initial = stateHolder.uiState.value
    assertThat(initial.suras).hasSize(12)
    assertThat(initial.juzs).isEmpty()
    assertThat(initial.hizbs).isEmpty()

    // Selecting JUZ before startObserving hydrates juzs immediately
    stateHolder.selectIndexSegment(HubIndexSegment.JUZ)
    assertThat(stateHolder.uiState.value.juzs).hasSize(30)

    stateHolder.startObserving(backgroundScope, latestPageFlow)
    assertThat(stateHolder.uiState.value.suras).hasSize(114)
    assertThat(stateHolder.uiState.value.juzs).hasSize(30)
    assertThat(stateHolder.uiState.value.hizbs).isEmpty()

    // Selecting HIZB populates the 60 hizb quarters lazily
    stateHolder.selectIndexSegment(HubIndexSegment.HIZB)
    assertThat(stateHolder.uiState.value.hizbs).hasSize(60)
  }

  @Test
  fun `latestPageFlow updates last location without incrementing pagesReadToday, while DailyWirdRepository records settled page reads idempotently`() = runTest(UnconfinedTestDispatcher()) {
    stateHolder.startObserving(backgroundScope, latestPageFlow)

    // Cold-start persisted recent page and transient page emissions update Hero location only
    latestPageFlow.value = 22
    latestPageFlow.value = 23
    val afterPersisted = stateHolder.uiState.value.dailyWird
    assertThat(afterPersisted.lastPage).isEqualTo(23)
    assertThat(afterPersisted.lastJuz).isEqualTo(2)
    assertThat(afterPersisted.lastSuraName).isNotEmpty()
    assertThat(afterPersisted.pagesReadToday).isEqualTo(0)

    // Settled reading in PagerActivity records into DailyWirdRepository idempotently per unique page
    dailyWirdRepository.recordPageRead(22)
    dailyWirdRepository.recordPageRead(22)
    assertThat(stateHolder.uiState.value.dailyWird.pagesReadToday).isEqualTo(1)

    dailyWirdRepository.recordPageRead(23)
    assertThat(stateHolder.uiState.value.dailyWird.pagesReadToday).isEqualTo(2)

    stateHolder.setDailyWirdGoal(10)
    assertThat(stateHolder.uiState.value.dailyWird.goalPages).isEqualTo(10)
    assertThat(stateHolder.uiState.value.dailyWird.progressPercent).isEqualTo(20)
  }

  @Test
  fun `bookmarks remain lazily uncollected until BOOKMARKS tab is selected`() = runTest(UnconfinedTestDispatcher()) {
    readingBookmarksDao.setReadingBookmark(
      PageReadingBookmark(ReadingBookmarkType.TEAL, 50, Instant.fromEpochSeconds(100), name = "ختمتي")
    )
    recentPagesDao.addRecentPage(22)
    highlightsDao.setHighlight(SuraAyah(2, 255), HighlightColor.GREEN)

    stateHolder.startObserving(backgroundScope, latestPageFlow)
    assertThat(stateHolder.uiState.value.bookmarks.isLoaded).isFalse()
    assertThat(stateHolder.uiState.value.bookmarks.readingBookmarks).isEmpty()

    // Selecting SEARCH still does not collect bookmarks
    stateHolder.selectNavTab(HybridNavTab.SEARCH)
    assertThat(stateHolder.uiState.value.bookmarks.isLoaded).isFalse()

    // Selecting BOOKMARKS triggers lazy collection and populates all sections
    stateHolder.selectNavTab(HybridNavTab.BOOKMARKS)
    val loaded = stateHolder.uiState.value.bookmarks
    assertThat(loaded.isLoaded).isTrue()
    assertThat(loaded.readingBookmarks).hasSize(1)
    assertThat(loaded.readingBookmarks.first().slot).isEqualTo(ReadingBookmarkType.TEAL)
    assertThat(loaded.readingBookmarks.first().page).isEqualTo(50)
    assertThat(loaded.readingBookmarks.first().customName).isEqualTo("ختمتي")
    assertThat(loaded.recentPages).hasSize(1)
    assertThat(loaded.recentPages.first().page).isEqualTo(22)
    assertThat(loaded.highlights).hasSize(1)
    assertThat(loaded.highlights.first().sura).isEqualTo(2)
    assertThat(loaded.highlights.first().ayah).isEqualTo(255)
    assertThat(loaded.highlights.first().color).isEqualTo(HighlightColor.GREEN)
  }

  @Test
  fun `toggleAyahBookmark adds to default collection and removes when toggled again`() = runTest(UnconfinedTestDispatcher()) {
    stateHolder.startObserving(backgroundScope, latestPageFlow)
    stateHolder.selectNavTab(HybridNavTab.BOOKMARKS)

    val added = stateHolder.toggleAyahBookmark(2, 255)
    assertThat(added).isTrue()
    assertThat(bookmarksDao.isSuraAyahBookmarked(SuraAyah(2, 255))).isTrue()
    assertThat(stateHolder.uiState.value.bookmarks.ayahBookmarks).hasSize(1)

    val removed = stateHolder.toggleAyahBookmark(2, 255)
    assertThat(removed).isTrue()
    assertThat(bookmarksDao.isSuraAyahBookmarked(SuraAyah(2, 255))).isFalse()
    assertThat(stateHolder.uiState.value.bookmarks.ayahBookmarks).isEmpty()
  }

  @Test
  fun `updateThemeStyle and updateThemeMode update state and persist preferences`() {
    stateHolder.updateThemeStyle(AppThemeStyle.BEIGE)
    assertThat(stateHolder.uiState.value.themeStyle).isEqualTo(AppThemeStyle.BEIGE)
    assertThat(settings.appThemeStyle).isEqualTo(AppThemeStyle.BEIGE.storageKey)

    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_LIGHT
    stateHolder.updateThemeMode(Constants.THEME_DARK)
    assertThat(stateHolder.uiState.value.appTheme).isEqualTo(Constants.THEME_DARK)
    assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)

    try {
      stateHolder.updateOledDarkMode(true)
      assertThat(stateHolder.uiState.value.isOledDarkMode).isTrue()
      assertThat(settings.isOledDarkMode).isTrue()
      assertThat(AppThemeStyle.isOledDarkMode).isTrue()

      settings.isOledDarkMode = false
      stateHolder.syncThemeFromSettings()
      assertThat(stateHolder.uiState.value.isOledDarkMode).isFalse()
      assertThat(AppThemeStyle.isOledDarkMode).isFalse()
    } finally {
      settings.isOledDarkMode = false
      AppThemeStyle.setOledDarkMode(false)
    }
  }
}
