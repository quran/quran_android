package com.quran.labs.androidquran.presenter.translation

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.presenter.translationlist.TranslationListCallback
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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

@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BaseTranslationPresenterLeakTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val translationsFlow = MutableSharedFlow<List<LocalTranslation>>(replay = 0)
  private var activeCollectors = 0
  private val observedCallbacks = mutableListOf<List<LocalTranslation>>()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    activeCollectors = 0
    observedCallbacks.clear()
  }

  @After
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `collects translations only while bound and cancels on unbind`() = runTest {
    val quranInfo = QuranInfo(MadaniDataSource())
    val translationListPresenter = object : TranslationListPresenter {
      override fun translations(): Flow<List<LocalTranslation>> {
        return translationsFlow
          .onStart { activeCollectors++ }
          .onCompletion { activeCollectors-- }
      }

      override fun registerForTranslations(callback: TranslationListCallback): Job = Job()
    }
    val presenter = object : BaseTranslationPresenter<FakeScreen>(
      FakeTranslationModel(),
      FakeTranslationsDBAdapter(),
      object : TranslationUtil(quranInfo) {
        override fun parseTranslationText(quranText: QuranText, translationId: Int): TranslationMetadata {
          return TranslationMetadata(quranText.sura, quranText.ayah, quranText.text, translationId)
        }
      },
      quranInfo,
      translationListPresenter
    ) {
      override fun onAvailableTranslationsChanged(localTranslations: List<LocalTranslation>) {
        observedCallbacks.add(localTranslations)
      }
    }

    val screen = FakeScreen()
    assertThat(activeCollectors).isEqualTo(0)

    presenter.bind(screen)
    assertThat(activeCollectors).isEqualTo(1)

    val sample = listOf(LocalTranslation(1, "en.sahih", "Sahih", "Sahih", null, "", null, 1, 2))
    translationsFlow.emit(sample)
    assertThat(observedCallbacks).hasSize(1)

    // Unbinding a different screen does not cancel the current binding
    presenter.unbind(FakeScreen())
    assertThat(activeCollectors).isEqualTo(1)

    presenter.unbind(screen)
    assertThat(activeCollectors).isEqualTo(0)
    assertThat(presenter.translationScreen).isNull()

    // Emissions after unbind do not reach presenter
    translationsFlow.emit(sample)
    assertThat(observedCallbacks).hasSize(1)

    // Re-bind works cleanly
    presenter.bind(screen)
    assertThat(activeCollectors).isEqualTo(1)
    presenter.unbind(screen)
    assertThat(activeCollectors).isEqualTo(0)
  }

  private class FakeScreen : Presenter<Any> {
    override fun bind(what: Any) {}
    override fun unbind(what: Any) {}
  }
}
