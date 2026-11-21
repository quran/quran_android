package com.quran.labs.androidquran.presenter.translation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.fakes.FakeTranslationListPresenter
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationScreen
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TranslationPresenterTest {

  private val quranInfo = QuranInfo(MadaniDataSource())
  private val page = 2
  private val pageVerses = quranInfo.getVerseRangeForPage(page)

  private lateinit var translationModel: FakeTranslationModel
  private lateinit var presenter: TranslationPresenter
  private lateinit var screen: FakeTranslationScreen

  @Before
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    QuranSettings.setInstance(null)
    val context: Context = ApplicationProvider.getApplicationContext()

    val translationsAdapter = FakeTranslationsDBAdapter()
    translationsAdapter.setTranslations(listOf(LocalTranslation(id = 1, filename = TRANSLATION)))
    translationModel = FakeTranslationModel()
    presenter = TranslationPresenter(
      translationModel,
      QuranSettings.getInstance(context),
      translationsAdapter,
      object : TranslationUtil(quranInfo) {
        override fun parseTranslationText(quranText: QuranText, translationId: Int): TranslationMetadata {
          return TranslationMetadata(quranText.sura, quranText.ayah, quranText.text, translationId)
        }
      },
      FakeTranslationListPresenter(),
      quranInfo,
      intArrayOf(page)
    )
    screen = FakeTranslationScreen()
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
    Dispatchers.resetMain()
  }

  @Test
  fun `restores the saved scroll position when the verses are first shown`() = runTest {
    givenVersesForPage()
    presenter.bind(screen)

    presenter.refresh()

    assertThat(screen.shownPages).containsExactly(page)
    assertThat(screen.scrollPositionUpdates).isEqualTo(1)
  }

  @Test
  fun `refreshing again keeps the position the reader scrolled to`() = runTest {
    givenVersesForPage()
    presenter.bind(screen)
    presenter.refresh()

    // returning to the page (ex after dismissing the share sheet) rebinds and refreshes it
    presenter.unbind(screen)
    presenter.bind(screen)
    presenter.refresh()

    assertThat(screen.shownPages).containsExactly(page, page)
    assertThat(screen.scrollPositionUpdates).isEqualTo(1)
  }

  @Test
  fun `restores the saved scroll position once verses are available`() = runTest {
    presenter.bind(screen)
    presenter.refresh()
    assertThat(screen.shownPages).isEmpty()
    assertThat(screen.scrollPositionUpdates).isEqualTo(0)

    givenVersesForPage()
    presenter.refresh()

    assertThat(screen.shownPages).containsExactly(page)
    assertThat(screen.scrollPositionUpdates).isEqualTo(1)
  }

  private fun givenVersesForPage() {
    val ayahs = pageVerses.startAyah..pageVerses.endingAyah
    translationModel.setArabicText(
      pageVerses, ayahs.map { QuranText(pageVerses.startSura, it, "arabic $it") }
    )
    translationModel.setTranslationText(
      pageVerses, TRANSLATION, ayahs.map { QuranText(pageVerses.startSura, it, "translation $it") }
    )
  }

  companion object {
    private const val TRANSLATION = "translation.db"
  }
}
