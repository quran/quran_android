package com.quran.mobile.feature.voicesearch.di

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.di.InlineVoiceSearchState
import com.quran.mobile.feature.voicesearch.asr.AsrEngine
import com.quran.mobile.feature.voicesearch.asr.AsrModelManager
import com.quran.mobile.feature.voicesearch.asr.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class InlineVoiceSearchControllerImplTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var asrEngine: AsrEngine
  private lateinit var asrModelManager: AsrModelManager
  private lateinit var preferencesProvider: VoiceSearchPreferencesProvider
  private lateinit var modelStateFlow: MutableStateFlow<ModelState>

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    asrEngine = mock(AsrEngine::class.java)
    asrModelManager = mock(AsrModelManager::class.java)
    preferencesProvider = mock(VoiceSearchPreferencesProvider::class.java)
    modelStateFlow = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    `when`(asrModelManager.modelState).thenReturn(modelStateFlow)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createController(): InlineVoiceSearchControllerImpl {
    return InlineVoiceSearchControllerImpl(asrEngine, asrModelManager, preferencesProvider)
  }

  @Test
  fun initialState_isIdle() {
    val controller = createController()
    assertThat(controller.state.value).isEqualTo(InlineVoiceSearchState.Idle)
  }

  @Test
  fun isEnabled_delegatesToPreferencesProvider() {
    `when`(preferencesProvider.isVoiceSearchEnabled()).thenReturn(true)
    val controller = createController()
    assertThat(controller.isEnabled).isTrue()

    `when`(preferencesProvider.isVoiceSearchEnabled()).thenReturn(false)
    assertThat(controller.isEnabled).isFalse()
  }

  @Test
  fun startRecording_modelNotReady_setsModelNotReadyState() {
    `when`(asrModelManager.isModelReady()).thenReturn(false)
    val controller = createController()

    controller.startRecording()

    assertThat(controller.state.value).isEqualTo(InlineVoiceSearchState.ModelNotReady)
  }

  @Test
  fun startRecording_alreadyRecording_isIgnored() {
    `when`(asrModelManager.isModelReady()).thenReturn(true)
    val controller = createController()

    // First call starts recording
    controller.startRecording()
    val stateAfterFirst = controller.state.value
    assertThat(stateAfterFirst).isInstanceOf(InlineVoiceSearchState.Recording::class.java)

    // Second call should be ignored (state unchanged)
    controller.startRecording()
    assertThat(controller.state.value).isInstanceOf(InlineVoiceSearchState.Recording::class.java)
  }

  @Test
  fun reset_returnsToIdle() {
    `when`(asrModelManager.isModelReady()).thenReturn(false)
    val controller = createController()

    controller.startRecording()
    assertThat(controller.state.value).isEqualTo(InlineVoiceSearchState.ModelNotReady)

    controller.reset()
    assertThat(controller.state.value).isEqualTo(InlineVoiceSearchState.Idle)
  }

  @Test
  fun release_callsResetAndEngineRelease() {
    val controller = createController()

    controller.release()

    assertThat(controller.state.value).isEqualTo(InlineVoiceSearchState.Idle)
    verify(asrEngine).release()
  }
}
