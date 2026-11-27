package com.quran.labs.androidquran.hybrid.reader

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.playback.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract.StudyTab
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.state.PageColor
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridReaderControllerTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo
  private lateinit var quranDisplayData: QuranDisplayData
  private lateinit var quranSettings: QuranSettings
  private lateinit var wirdPrefs: SharedPreferences
  private lateinit var defaultPrefs: SharedPreferences
  private lateinit var dailyWirdRepository: DailyWirdRepository
  private lateinit var audioStatusRepository: AudioStatusRepository
  private lateinit var qariManager: CurrentQariManager
  private lateinit var downloadInfoStreams: DownloadInfoStreams
  private lateinit var bookmarksDao: FakeBookmarksDao
  private lateinit var translationsDBAdapter: FakeTranslationsDBAdapter
  private lateinit var translationModel: FakeTranslationModel
  private lateinit var controllerScope: CoroutineScope

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
    quranDisplayData = QuranDisplayData(quranInfo)
    quranSettings = QuranSettings.getInstance(context)
    quranSettings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_LIGHT
    quranSettings.pageTheme = PageThemeConstants.PAGE_THEME_LIGHT
    wirdPrefs = context.getSharedPreferences("test_controller_wird", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }
    defaultPrefs = context.getSharedPreferences("test_controller_default", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }
    dailyWirdRepository = DailyWirdRepository(wirdPrefs) { 20_100L }
    audioStatusRepository = AudioStatusRepository()
    val testQari = Qari(
      id = 1,
      nameResource = R.string.app_name,
      url = "https://example.com/",
      path = "husary",
      hasGaplessAlternative = true,
      db = "husary.db"
    )
    qariManager = CurrentQariManager(context, QariUtil(FakePageProvider(listOf(testQari))))
    downloadInfoStreams = DownloadInfoStreams()
    bookmarksDao = FakeBookmarksDao { quranInfo.getPageFromSuraAyah(it.sura, it.ayah) }
    translationsDBAdapter = FakeTranslationsDBAdapter()
    translationModel = FakeTranslationModel()
    AppThemeStyle.select(AppThemeStyle.DEFAULT)
    AppThemeStyle.setOledDarkMode(false)
  }

  @After
  fun tearDown() {
    if (::controllerScope.isInitialized) {
      controllerScope.cancel()
    }
    QuranSettings.setInstance(null)
    AppThemeStyle.select(AppThemeStyle.DEFAULT)
    AppThemeStyle.setOledDarkMode(false)
  }

  private fun createController(dispatcher: kotlinx.coroutines.CoroutineDispatcher): HybridReaderController {
    controllerScope = CoroutineScope(SupervisorJob() + dispatcher)
    val tafsirHelper = TafsirLookupHelper(
      translationsDBAdapter = translationsDBAdapter,
      translationModel = translationModel,
      translationUtil = TranslationUtil(quranInfo),
      ioDispatcher = dispatcher
    )
    return HybridReaderController(
      appContext = context,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      quranSettings = quranSettings,
      dailyWirdRepository = dailyWirdRepository,
      audioStatusRepository = audioStatusRepository,
      currentQariManager = qariManager,
      downloadInfoStreams = downloadInfoStreams,
      timingRepository = TimingRepository(),
      bookmarksDao = bookmarksDao,
      tafsirLookupHelper = tafsirHelper,
      scope = controllerScope,
      ioDispatcher = dispatcher,
      defaultPrefs = defaultPrefs
    ).apply { startObserving() }
  }

  @Test
  fun `recordSettledPage updates DailyWirdRepository idempotently with sura and juz`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val controller = createController(dispatcher)

    controller.updateCurrentPage(2)
    controller.recordSettledPage(2)
    controller.recordSettledPage(2) // duplicate settle ignored

    val wird = dailyWirdRepository.stateFlow.value
    assertThat(wird.pagesReadToday).isEqualTo(1)
    assertThat(wird.lastPage).isEqualTo(2)
    assertThat(wird.lastJuz).isEqualTo(1)
    val expectedSuraName = quranDisplayData.getSuraName(
      context,
      2,
      wantPrefix = context.resources.getBoolean(R.bool.show_surat_prefix)
    )
    assertThat(wird.lastSuraName).isEqualTo(expectedSuraName)
  }

  @Test
  fun `recordPageReadAndLocation coalesces page read and location into single consistent state`() = runTest {
    dailyWirdRepository.recordPageReadAndLocation(
      page = 42,
      suraName = "سورة البقرة",
      ayah = 255,
      juz = 3
    )
    val wird = dailyWirdRepository.stateFlow.value
    assertThat(wird.pagesReadToday).isEqualTo(1)
    assertThat(wird.lastPage).isEqualTo(42)
    assertThat(wird.lastSuraName).isEqualTo("سورة البقرة")
    assertThat(wird.lastAyah).isEqualTo(255)
    assertThat(wird.lastJuz).isEqualTo(3)
  }

  @Test
  fun `opening Tafsir study tab preserves ayah selection and loads Muyassar tafsir`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    translationsDBAdapter.setTranslations(
      listOf(LocalTranslation(id = 1L, filename = "ar.muyassar", name = "التفسير الميسر", languageCode = "ar"))
    )
    translationModel.setTranslationText(
      VerseRange(1, 7, 1, 7, 1),
      "ar.muyassar",
      listOf(QuranText(1, 7, "صراط الذين أنعمت عليهم"))
    )

    val controller = createController(dispatcher)
    val bounds = AyahOverlayBounds(10f, 100f, 200f, 140f, 10f, 140f, 200f, 180f)
    controller.onAyahSelectionChanged(SuraAyah(1, 7), SuraAyah(1, 7), bounds)
    controller.openStudyDrawer(StudyTab.TAFSIR)

    val state = controller.uiState.value
    assertThat(state.isStudyDrawerOpen).isTrue()
    assertThat(state.selectedStudyTab).isEqualTo(StudyTab.TAFSIR)
    assertThat(state.selectedStart).isEqualTo(SuraAyah(1, 7))
    assertThat(state.tafsirState.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(state.tafsirState.fullText).isEqualTo("صراط الذين أنعمت عليهم")
  }

  @Test
  fun `adjacentAyah steps cleanly across sura boundaries and stops at Quran bounds`() = runTest {
    val controller = createController(UnconfinedTestDispatcher(testScheduler))

    assertThat(controller.adjacentAyah(SuraAyah(1, 1), next = false)).isNull()
    assertThat(controller.adjacentAyah(SuraAyah(1, 7), next = true)).isEqualTo(SuraAyah(2, 1))
    assertThat(controller.adjacentAyah(SuraAyah(2, 1), next = false)).isEqualTo(SuraAyah(1, 7))
    assertThat(controller.adjacentAyah(SuraAyah(114, 6), next = true)).isNull()
  }

  @Test
  fun `audio playback status transitions and bookmarks changes reflect in uiState`() = runTest {
    val controller = createController(UnconfinedTestDispatcher(testScheduler))
    val ayah = SuraAyah(2, 255)
    controller.onAyahSelectionChanged(ayah, ayah, AyahOverlayBounds(0f, 10f, 100f, 50f, 0f, 10f, 100f, 50f))
    assertThat(controller.uiState.value.isSelectedAyahBookmarked).isFalse()

    bookmarksDao.replaceAyahBookmarkCollections(ayah, setOf("fake-default"))
    assertThat(controller.uiState.value.isSelectedAyahBookmarked).isTrue()

    val request = AudioRequest(
      start = SuraAyah(2, 255),
      end = SuraAyah(2, 257),
      qari = QariItem(1, "Al-Husary", "", path = "husary", hasGaplessAlternative = false),
      playbackSpeed = 1.25f,
      repeatInfo = 2,
      enforceBounds = true,
      shouldStream = false,
      audioPathInfo = AudioPathInfo("", "", null, listOf("mp3"))
    )

    controller.uiState.test {
      awaitItem() // current state
      audioStatusRepository.updateAyahPlayback(
        AudioStatus.Playback(SuraAyah(2, 255), request, PlaybackStatus.PLAYING)
      )
      val playing = awaitItem()
      assertThat(playing.isPlaying).isTrue()
      assertThat(playing.playbackSpeed).isEqualTo(1.25f)
      assertThat(playing.repeatCount).isEqualTo(2)

      audioStatusRepository.updateAyahPlayback(
        AudioStatus.Playback(SuraAyah(2, 255), request, PlaybackStatus.PAUSED)
      )
      val paused = awaitItem()
      assertThat(paused.isPlaying).isFalse()
      assertThat(paused.playbackStatus).isEqualTo(PlaybackStatus.PAUSED)

      audioStatusRepository.updateAyahPlayback(AudioStatus.Stopped)
      val stopped = awaitItem()
      assertThat(stopped.isAudioActive).isFalse()
      assertThat(stopped.playbackStatus).isNull()
    }
  }

  @Test
  fun `appearance tab mutators persist calm palette, app mode, page theme, and night brightness`() = runTest {
    val controller = createController(UnconfinedTestDispatcher(testScheduler))

    controller.selectAppThemeStyle(AppThemeStyle.BEIGE)
    assertThat(AppThemeStyle.current).isEqualTo(AppThemeStyle.BEIGE)
    assertThat(quranSettings.appThemeStyle).isEqualTo("beige")

    controller.selectAppThemeMode(Constants.THEME_DARK)
    assertThat(defaultPrefs.getString(Constants.PREF_APP_THEME, null)).isEqualTo(Constants.THEME_DARK)
    assertThat(quranSettings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
    android.preference.PreferenceManager.getDefaultSharedPreferences(context)
      .edit().putString(Constants.PREF_APP_THEME, Constants.THEME_DARK).commit()
    assertThat(quranSettings.isNightMode).isTrue()

    controller.selectPageColor(PageColor.Sepia)
    assertThat(quranSettings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_SEPIA)
    assertThat(quranSettings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)

    controller.selectPageColor(PageColor.Dark)
    assertThat(quranSettings.isNightMode).isTrue()

    controller.setNightTextBrightness(200)
    controller.setNightBackgroundBrightness(24)
    assertThat(quranSettings.nightModeTextBrightness).isEqualTo(200)
    assertThat(quranSettings.nightModeBackgroundBrightness).isEqualTo(24)

    controller.setMatchAppTheme(true)
    assertThat(quranSettings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
  }

  @Test
  fun `refreshTafsirAfterResume resets loadState while drawer is closed and reloads updated DB on reopen for same ayah`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val controller = createController(dispatcher)
    val bounds = AyahOverlayBounds(10f, 100f, 200f, 140f, 10f, 140f, 200f, 180f)
    controller.onAyahSelectionChanged(SuraAyah(1, 7), SuraAyah(1, 7), bounds)

    // Initially no tafsir DB installed -> EMPTY
    translationsDBAdapter.setTranslations(emptyList())
    controller.openStudyDrawer(StudyTab.TAFSIR)
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.EMPTY)

    // User closes Study Drawer, installs Muyassar, and returns to PagerActivity (onResume -> refreshTafsirAfterResume)
    controller.closeStudyDrawer()
    assertThat(controller.uiState.value.isStudyDrawerOpen).isFalse()

    translationsDBAdapter.setTranslations(
      listOf(LocalTranslation(id = 1L, filename = "ar.muyassar", name = "التفسير الميسر", languageCode = "ar"))
    )
    translationModel.setTranslationText(
      VerseRange(1, 7, 1, 7, 1),
      "ar.muyassar",
      listOf(QuranText(1, 7, "تفسير ميسر محدث"))
    )
    controller.refreshTafsirAfterResume()
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.IDLE)

    // Reopening on the SAME ayah (1, 7) must reload from updated DB
    controller.openStudyDrawer(StudyTab.TAFSIR)
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(controller.uiState.value.tafsirState.fullText).isEqualTo("تفسير ميسر محدث")

    // Now test LOADED -> close -> update DB -> resume -> reopen on same ayah (1, 7)
    controller.closeStudyDrawer()
    translationModel.setTranslationText(
      VerseRange(1, 7, 1, 7, 1),
      "ar.muyassar",
      listOf(QuranText(1, 7, "تفسير ميسر بعد التحديث الثاني"))
    )
    controller.refreshTafsirAfterResume()
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.IDLE)

    controller.openStudyDrawer(StudyTab.TAFSIR)
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(controller.uiState.value.tafsirState.fullText).isEqualTo("تفسير ميسر بعد التحديث الثاني")
  }

  @Test
  fun `onAyahSelectionChanged populates surah-prefixed selectedSuraName while top bar suraName stays unprefixed`() = runTest {
    val arabicConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arabicContext = context.createConfigurationContext(arabicConfig)
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    controllerScope = CoroutineScope(SupervisorJob() + dispatcher)
    val controller = HybridReaderController(
      appContext = arabicContext,
      quranInfo = quranInfo,
      quranDisplayData = QuranDisplayData(quranInfo),
      quranSettings = quranSettings,
      dailyWirdRepository = dailyWirdRepository,
      audioStatusRepository = audioStatusRepository,
      currentQariManager = qariManager,
      downloadInfoStreams = downloadInfoStreams,
      timingRepository = TimingRepository(),
      bookmarksDao = bookmarksDao,
      tafsirLookupHelper = TafsirLookupHelper(
        translationsDBAdapter = translationsDBAdapter,
        translationModel = translationModel,
        translationUtil = TranslationUtil(quranInfo),
        ioDispatcher = dispatcher
      ),
      scope = controllerScope,
      ioDispatcher = dispatcher,
      defaultPrefs = defaultPrefs
    )

    controller.updateCurrentPage(1)
    assertThat(controller.uiState.value.suraName).isEqualTo("الفَاتِحَةِ")

    val bounds = AyahOverlayBounds(100f, 300f, 500f, 360f, 120f, 360f, 480f, 420f)
    controller.onAyahSelectionChanged(SuraAyah(1, 2), SuraAyah(1, 2), bounds)
    assertThat(controller.uiState.value.selectedSuraName).isEqualTo("سُورَةُ الفَاتِحَةِ")
    assertThat(controller.uiState.value.suraName).isEqualTo("الفَاتِحَةِ")

    // Multi-surah page 604 (Al-Ikhlas 112, Al-Falaq 113, An-Nas 114): selecting An-Nas uses start.sura
    controller.updateCurrentPage(604)
    controller.onAyahSelectionChanged(SuraAyah(114, 1), SuraAyah(114, 1), bounds)
    assertThat(controller.uiState.value.selectedSuraName).isEqualTo("سُورَةُ النَّاسِ")
    assertThat(controller.uiState.value.suraName).isEqualTo("الإِخۡلَاصِ")

    controller.onAyahSelectionChanged(null, null, null)
    assertThat(controller.uiState.value.selectedSuraName).isEmpty()
  }

  @Test
  fun `openStudyDrawer Recitation does not load tafsir until user switches to Tafsir tab`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    translationsDBAdapter.setTranslations(
      listOf(LocalTranslation(id = 1L, filename = "ar.muyassar", name = "التفسير الميسر", languageCode = "ar"))
    )
    translationModel.setTranslationText(
      VerseRange(1, 1, 1, 1, 1),
      "ar.muyassar",
      listOf(QuranText(1, 1, "تفسير البسملة"))
    )

    val controller = createController(dispatcher)
    controller.openStudyDrawer(StudyTab.RECITATION)
    assertThat(controller.uiState.value.isStudyDrawerOpen).isTrue()
    assertThat(controller.uiState.value.selectedStudyTab).isEqualTo(StudyTab.RECITATION)
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.IDLE)

    // Calling preloadStudyTafsirIfNeeded while on Recitation tab must NOT load Tafsir
    controller.preloadStudyTafsirIfNeeded()
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.IDLE)

    // Switching explicitly to Tafsir tab loads Tafsir cleanly
    controller.selectStudyTab(StudyTab.TAFSIR)
    assertThat(controller.uiState.value.tafsirState.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(controller.uiState.value.tafsirState.fullText).isEqualTo("تفسير البسملة")
  }

  @Test
  fun `standalone appearance sheet is mutually exclusive with study drawer, stays open across mutations, and handles legacy StudyTab APPEARANCE`() = runTest {
    val controller = createController(UnconfinedTestDispatcher(testScheduler))

    // Open Study Drawer first -> opening Appearance Sheet closes Study Drawer
    controller.openStudyDrawer(StudyTab.RECITATION)
    assertThat(controller.uiState.value.isStudyDrawerOpen).isTrue()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isFalse()

    controller.openAppearanceSheet()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    assertThat(controller.uiState.value.isStudyDrawerOpen).isFalse()

    // Every appearance tweak MUST keep the standalone Appearance Sheet open
    controller.selectAppThemeMode(Constants.THEME_DARK)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    controller.selectAppThemeStyle(AppThemeStyle.BLUE)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    controller.selectPageColor(PageColor.Sepia)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    controller.setMatchAppTheme(false)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    controller.setNightTextBrightness(210)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    controller.setNightBackgroundBrightness(18)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()

    // Opening Study Drawer closes Appearance Sheet cleanly
    controller.openStudyDrawer(StudyTab.TAFSIR)
    assertThat(controller.uiState.value.isStudyDrawerOpen).isTrue()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isFalse()

    // Legacy callers passing StudyTab.APPEARANCE route gracefully to standalone Appearance Sheet
    controller.openStudyDrawer(StudyTab.APPEARANCE)
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    assertThat(controller.uiState.value.isStudyDrawerOpen).isFalse()

    controller.closeAppearanceSheet()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isFalse()

    // Restoring saved state re-opens standalone Appearance Sheet after config/night-mode recreation
    controller.restoreUiState(
      isDrawerOpen = false,
      studyTab = StudyTab.RECITATION,
      isAppearanceSheetOpen = true
    )
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    assertThat(controller.uiState.value.isStudyDrawerOpen).isFalse()
  }

  @Test
  fun `switching reciter while paused keeps newly selected reciter name despite stale paused AudioStatus`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val qariA = Qari(
      id = 1,
      nameResource = R.string.app_name,
      url = "https://example.com/a/",
      path = "qari_a",
      hasGaplessAlternative = false,
      db = null
    )
    val qariB = Qari(
      id = 2,
      nameResource = R.string.stop,
      url = "https://example.com/b/",
      path = "qari_b",
      hasGaplessAlternative = false,
      db = null
    )
    qariManager = CurrentQariManager(context, QariUtil(FakePageProvider(listOf(qariA, qariB))))
    qariManager.setCurrentQari(1)
    val controller = createController(dispatcher)

    val reqA = AudioRequest(
      start = SuraAyah(1, 1),
      end = SuraAyah(1, 7),
      qari = QariItem(1, "Reciter A", "", path = "qari_a", hasGaplessAlternative = false),
      playbackSpeed = 1.0f,
      repeatInfo = 0,
      enforceBounds = true,
      shouldStream = false,
      audioPathInfo = AudioPathInfo("", "", null, listOf("mp3"))
    )
    audioStatusRepository.updateAyahPlayback(
      AudioStatus.Playback(SuraAyah(1, 2), reqA, PlaybackStatus.PAUSED)
    )
    assertThat(controller.uiState.value.qariFullName).isEqualTo("Reciter A")

    // User switches to Reciter B while paused on Reciter A
    qariManager.setCurrentQari(2)
    val expectedBName = context.getString(R.string.stop)
    assertThat(controller.uiState.value.qariFullName).isEqualTo(expectedBName)

    // Stale PAUSED playback emission from Reciter A must NOT overwrite Reciter B's name
    audioStatusRepository.updateAyahPlayback(
      AudioStatus.Playback(SuraAyah(1, 2), reqA, PlaybackStatus.PAUSED)
    )
    assertThat(controller.uiState.value.qariFullName).isEqualTo(expectedBName)
    assertThat(controller.uiState.value.qariShortName).isEqualTo(expectedBName)
    assertThat(controller.uiState.value.playbackStatus).isEqualTo(PlaybackStatus.PAUSED)
  }

  @Test
  fun `setOledDarkMode persists setting updates AppThemeStyle and keeps standalone appearance sheet open`() = runTest {
    val controller = createController(UnconfinedTestDispatcher(testScheduler))

    controller.openAppearanceSheet()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
    assertThat(controller.uiState.value.appearanceState.isOledDarkMode).isFalse()

    controller.setOledDarkMode(true)
    assertThat(quranSettings.isOledDarkMode).isTrue()
    assertThat(AppThemeStyle.isOledDarkMode).isTrue()
    assertThat(controller.uiState.value.appearanceState.isOledDarkMode).isTrue()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()

    controller.setOledDarkMode(false)
    assertThat(quranSettings.isOledDarkMode).isFalse()
    assertThat(AppThemeStyle.isOledDarkMode).isFalse()
    assertThat(controller.uiState.value.appearanceState.isOledDarkMode).isFalse()
    assertThat(controller.uiState.value.isAppearanceSheetOpen).isTrue()
  }
}
