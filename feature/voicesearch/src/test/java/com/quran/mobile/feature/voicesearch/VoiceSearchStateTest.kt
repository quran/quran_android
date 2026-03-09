package com.quran.mobile.feature.voicesearch

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.voicesearch.asr.ModelState
import org.junit.Test

class VoiceSearchStateTest {

  @Test
  fun defaultState_hasExpectedDefaults() {
    val state = VoiceSearchState()
    assertThat(state.screenState).isEqualTo(ScreenState.Idle)
    assertThat(state.modelState).isEqualTo(ModelState.NotDownloaded)
    assertThat(state.transcribedText).isEmpty()
    assertThat(state.verseMatches).isEmpty()
    assertThat(state.amplitude).isEqualTo(0f)
    assertThat(state.errorMessage).isNull()
  }

  @Test
  fun copy_preservesUnmodifiedFields() {
    val state = VoiceSearchState(
      screenState = ScreenState.Recording,
      amplitude = 0.5f
    )
    val updated = state.copy(amplitude = 0.8f)

    assertThat(updated.screenState).isEqualTo(ScreenState.Recording)
    assertThat(updated.amplitude).isEqualTo(0.8f)
    assertThat(updated.transcribedText).isEmpty()
  }

  @Test
  fun screenState_hasAllExpectedValues() {
    val values = ScreenState.entries
    assertThat(values).containsExactly(
      ScreenState.Idle,
      ScreenState.ModelDownloading,
      ScreenState.Ready,
      ScreenState.Recording,
      ScreenState.Transcribing,
      ScreenState.Results
    )
  }

  @Test
  fun modelState_sealed_variants() {
    val notDownloaded: ModelState = ModelState.NotDownloaded
    val downloading: ModelState = ModelState.Downloading(0.5f)
    val ready: ModelState = ModelState.Ready
    val error: ModelState = ModelState.Error("fail")

    assertThat(notDownloaded).isInstanceOf(ModelState.NotDownloaded::class.java)
    assertThat(downloading).isInstanceOf(ModelState.Downloading::class.java)
    assertThat((downloading as ModelState.Downloading).progress).isEqualTo(0.5f)
    assertThat(ready).isInstanceOf(ModelState.Ready::class.java)
    assertThat((error as ModelState.Error).message).isEqualTo("fail")
  }
}
