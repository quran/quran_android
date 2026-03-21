package com.quran.mobile.feature.voicesearch.asr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for AudioRecorder's sample buffer management.
 *
 * Since AudioRecorder depends on Android's AudioRecord for recording,
 * we test the buffer accumulation logic by exercising [getAccumulatedSamples]
 * on a fresh instance (which starts with an empty buffer).
 */
class AudioRecorderBufferTest {

  @Test
  fun freshRecorder_returnsEmptySamples() {
    val recorder = AudioRecorder()
    val samples = recorder.getAccumulatedSamples()
    assertThat(samples).isEmpty()
  }

  @Test
  fun getAccumulatedSamples_returnsCopy() {
    val recorder = AudioRecorder()
    val first = recorder.getAccumulatedSamples()
    val second = recorder.getAccumulatedSamples()
    assertThat(first).isNotSameInstanceAs(second)
  }

  @Test
  fun stopRecording_onFreshRecorder_doesNotThrow() {
    val recorder = AudioRecorder()
    recorder.stopRecording()
  }

  @Test
  fun sampleRate_isSixteenKHz() {
    assertThat(AudioRecorder.SAMPLE_RATE).isEqualTo(16000)
  }
}
