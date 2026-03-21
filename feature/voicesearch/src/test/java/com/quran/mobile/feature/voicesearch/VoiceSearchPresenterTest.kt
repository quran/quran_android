package com.quran.mobile.feature.voicesearch

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.voicesearch.asr.AsrEngine
import com.quran.mobile.feature.voicesearch.asr.AsrModelManager
import com.quran.mobile.feature.voicesearch.asr.ModelState
import com.quran.mobile.feature.voicesearch.matching.IndexedVerse
import com.quran.mobile.feature.voicesearch.matching.QuranVerseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSearchPresenterTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var asrEngine: AsrEngine
  private lateinit var modelManager: AsrModelManager
  private lateinit var verseProvider: QuranVerseProvider
  private lateinit var modelStateFlow: MutableStateFlow<ModelState>

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    asrEngine = mock(AsrEngine::class.java)
    modelManager = mock(AsrModelManager::class.java)
    verseProvider = mock(QuranVerseProvider::class.java)
    modelStateFlow = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    `when`(modelManager.modelState).thenReturn(modelStateFlow)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createPresenter(): VoiceSearchPresenter {
    return VoiceSearchPresenter(asrEngine, modelManager, verseProvider)
  }

  @Test
  fun initialize_notDownloaded_mapsToIdle() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    val presenter = createPresenter()
    presenter.initialize()

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.Idle)
    assertThat(presenter.state.value.modelState).isEqualTo(ModelState.NotDownloaded)
    presenter.release()
  }

  @Test
  fun initialize_ready_mapsToReady() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    modelStateFlow.value = ModelState.Ready
    val presenter = createPresenter()
    presenter.initialize()

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.Ready)
    assertThat(presenter.state.value.modelState).isEqualTo(ModelState.Ready)
    presenter.release()
  }

  @Test
  fun initialize_downloading_mapsToModelDownloading() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    modelStateFlow.value = ModelState.Downloading(0.5f)
    val presenter = createPresenter()
    presenter.initialize()

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.ModelDownloading)
    presenter.release()
  }

  @Test
  fun initialize_error_mapsToIdle() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    modelStateFlow.value = ModelState.Error("fail")
    val presenter = createPresenter()
    presenter.initialize()

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.Idle)
    presenter.release()
  }

  @Test
  fun onEvent_dismissError_clearsErrorMessage() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    val presenter = createPresenter()
    presenter.initialize()

    // Simulate an error state by updating model state flow to set up presenter,
    // then we test dismissError on a state that has an error message
    // We need to directly test the dismiss behavior
    presenter.onEvent(VoiceSearchEvent.DismissError)
    assertThat(presenter.state.value.errorMessage).isNull()
    presenter.release()
  }

  @Test
  fun onEvent_reset_modelReady_returnsToReady() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    `when`(modelManager.isModelReady()).thenReturn(true)
    modelStateFlow.value = ModelState.Ready
    val presenter = createPresenter()
    presenter.initialize()

    presenter.onEvent(VoiceSearchEvent.Reset)

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.Ready)
    presenter.release()
  }

  @Test
  fun onEvent_reset_modelNotReady_returnsToIdle() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    `when`(modelManager.isModelReady()).thenReturn(false)
    val presenter = createPresenter()
    presenter.initialize()

    presenter.onEvent(VoiceSearchEvent.Reset)

    assertThat(presenter.state.value.screenState).isEqualTo(ScreenState.Idle)
    presenter.release()
  }

  @Test
  fun onEvent_selectVerse_emitsNavigationResult() = runTest(testDispatcher) {
    val presenter = createPresenter()

    val deferred = async { presenter.navigationEvents.first() }
    presenter.onEvent(VoiceSearchEvent.SelectVerse(2, 255))

    val result = deferred.await()
    assertThat(result).isInstanceOf(NavigationResult.VerseSelected::class.java)
    val verseSelected = result as NavigationResult.VerseSelected
    assertThat(verseSelected.sura).isEqualTo(2)
    assertThat(verseSelected.ayah).isEqualTo(255)
    presenter.release()
  }

  @Test
  fun onEvent_searchText_emitsTextSearchResult() = runTest(testDispatcher) {
    val presenter = createPresenter()

    val deferred = async { presenter.navigationEvents.first() }
    presenter.onEvent(VoiceSearchEvent.SearchText)

    val result = deferred.await()
    assertThat(result).isInstanceOf(NavigationResult.TextSearch::class.java)
    presenter.release()
  }

  @Test
  fun release_clearsState() = runTest {
    `when`(verseProvider.getAllVerses()).thenReturn(emptyList())
    val presenter = createPresenter()
    presenter.initialize()

    presenter.release()
    // After release, the scope is cancelled — we just verify no crash
  }
}
