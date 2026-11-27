package com.quran.labs.androidquran.hybrid.reader

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.common.download.DownloadInfoStreams
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridReaderEdgeArgumentsTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
  }

  @Test
  fun `HybridReaderIntentParser handles invalid sura, ayah, malformed tab, and out-of-range explicit page`() {
    val invalidSuraIntent = Intent().apply {
      putExtra(HybridReaderContract.EXTRA_HIGHLIGHT_SURA, 115)
      putExtra(HybridReaderContract.EXTRA_HIGHLIGHT_AYAH, 1)
      putExtra(HybridReaderContract.EXTRA_INITIAL_STUDY_TAB, "invalid_tab")
    }
    val parsedInvalidSura = HybridReaderIntentParser.parse(invalidSuraIntent, quranInfo, fallbackPage = 7)
    assertThat(parsedInvalidSura.page).isEqualTo(7)
    assertThat(parsedInvalidSura.initialStudyTab).isNull()

    val zeroSuraIntent = Intent().apply {
      putExtra(HybridReaderContract.EXTRA_HIGHLIGHT_SURA, 0)
      putExtra(HybridReaderContract.EXTRA_HIGHLIGHT_AYAH, -5)
    }
    val parsedZero = HybridReaderIntentParser.parse(zeroSuraIntent, quranInfo, fallbackPage = 3)
    assertThat(parsedZero.highlightSura).isNull()
    assertThat(parsedZero.highlightAyah).isNull()
    assertThat(parsedZero.page).isEqualTo(3)

    // Explicit out-of-range page flags hasExplicitPage=true without crashing parser
    val badPageIntent = Intent().apply { putExtra("page", 605) }
    val parsedBadPage = HybridReaderIntentParser.parse(badPageIntent, quranInfo, fallbackPage = 1)
    assertThat(parsedBadPage.hasExplicitPage).isTrue()
    assertThat(parsedBadPage.page).isEqualTo(605)
  }

  @Test
  fun `HybridReaderController ignores out-of-bounds pages and survives invalid SuraAyah selection`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val prefs = context.getSharedPreferences("adv_ctrl_wird", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    val wirdRepo = DailyWirdRepository(prefs) { 20_500L }
    val dbAdapter = FakeTranslationsDBAdapter()
    val testQari = com.quran.data.model.audio.Qari(
      id = 1,
      nameResource = R.string.app_name,
      url = "https://example.com/",
      path = "husary",
      hasGaplessAlternative = true,
      db = "husary.db"
    )
    val controller = HybridReaderController(
      appContext = context,
      quranInfo = quranInfo,
      quranDisplayData = QuranDisplayData(quranInfo),
      quranSettings = QuranSettings.getInstance(context),
      dailyWirdRepository = wirdRepo,
      audioStatusRepository = AudioStatusRepository(),
      currentQariManager = CurrentQariManager(context, QariUtil(FakePageProvider(listOf(testQari)))),
      downloadInfoStreams = DownloadInfoStreams(),
      timingRepository = TimingRepository(),
      bookmarksDao = FakeBookmarksDao { quranInfo.getPageFromSuraAyah(it.sura, it.ayah) },
      tafsirLookupHelper = TafsirLookupHelper(
        dbAdapter,
        FakeTranslationModel(),
        TranslationUtil(quranInfo),
        ioDispatcher = dispatcher
      ),
      scope = scope,
      ioDispatcher = dispatcher,
      defaultPrefs = prefs
    )

    try {
      controller.updateCurrentPage(12)
      controller.updateCurrentPage(0)
      controller.updateCurrentPage(605)
      assertThat(controller.uiState.value.currentPage).isEqualTo(12)

      controller.recordSettledPage(0)
      controller.recordSettledPage(605)
      assertThat(wirdRepo.stateFlow.value.pagesReadToday).isEqualTo(0)

      controller.onAyahSelectionChanged(SuraAyah(115, 1), SuraAyah(115, 1), null)
      assertThat(controller.uiState.value.selectedSuraName).isEmpty()
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `TafsirLookupHelper handles empty DB results, missing preferredFilename, and failing arabic loader`() = runTest {
    val dbAdapter = FakeTranslationsDBAdapter()
    val model = FakeTranslationModel()
    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = model,
      translationUtil = TranslationUtil(quranInfo),
      arabicTextLoader = { _ -> throw SQLiteException("corrupt arabic db") },
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    // No installed sources -> EMPTY with null ayahArabicText (caught SQLiteException)
    val emptyInstalled = helper.loadTafsirForAyah(1, 1, "الفاتحة")
    assertThat(emptyInstalled.loadState).isEqualTo(TafsirLoadState.EMPTY)
    assertThat(emptyInstalled.selectedFilename).isNull()
    assertThat(emptyInstalled.ayahArabicText).isNull()

    // Installed source with missing preferredFilename + empty DB rows -> falls back to ar.muyassar cleanly
    val muyassar = LocalTranslation(id = 1L, filename = "ar.muyassar", name = "الميسر", languageCode = "ar")
    dbAdapter.setTranslations(listOf(muyassar))

    val res = helper.loadTafsirForAyah(
      sura = 1,
      ayah = 1,
      suraName = "الفاتحة",
      preferredFilename = "nonexistent.db"
    )
    assertThat(res.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(res.selectedFilename).isEqualTo("ar.muyassar")
    assertThat(res.ayahArabicText).isNull()
    assertThat(res.fullText).isEmpty()
  }
}
