package com.quran.labs.androidquran.common.audio.model.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSpeedTest {

  @Test
  fun `speeds include 1_75x and 2_0x`() {
    assertThat(PlaybackSpeed.speeds).containsAllOf(1.75f, 2.0f)
  }

  @Test
  fun `speeds still include the previously offered speeds`() {
    assertThat(PlaybackSpeed.speeds)
      .containsAllOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
  }

  @Test
  fun `speeds are in ascending order without duplicates`() {
    assertThat(PlaybackSpeed.speeds).isInOrder()
    assertThat(PlaybackSpeed.speeds).containsNoDuplicates()
  }

  @Test
  fun `default speed is one of the speeds`() {
    assertThat(PlaybackSpeed.speeds).contains(PlaybackSpeed.DEFAULT_SPEED)
    assertThat(PlaybackSpeed.DEFAULT_SPEED).isEqualTo(1.0f)
  }
}
