package com.quran.labs.androidquran.feature.reading

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadingHistoryPlacementTest {

  @Test
  fun `measured audio bar is the clearance and the navigation inset is not added again`() {
    assertThat(
      ReadingHistoryPlacement.bottomMargin(
        audioBarHeight = 180,
        navigationInset = 48,
        fallbackAudioHeight = 56,
        audioBarVisible = true
      )
    ).isEqualTo(180)
  }

  @Test
  fun `visible audio bar that is not measured yet reserves the fallback height`() {
    assertThat(
      ReadingHistoryPlacement.bottomMargin(
        audioBarHeight = 0,
        navigationInset = 48,
        fallbackAudioHeight = 56,
        audioBarVisible = true
      )
    ).isEqualTo(104)
  }

  @Test
  fun `hidden audio bar clears only the navigation inset`() {
    assertThat(
      ReadingHistoryPlacement.bottomMargin(
        audioBarHeight = 96,
        navigationInset = 48,
        fallbackAudioHeight = 56,
        audioBarVisible = false
      )
    ).isEqualTo(48)
  }
}
