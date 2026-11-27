package com.quran.labs.androidquran.extra.feature.linebyline.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranPageScrollLogicTest {

  @Test
  fun `shouldAnimateScrollTo returns false when target is negative or unchanged`() {
    assertThat(shouldAnimateScrollTo(targetScrollPosition = -1, currentScrollValue = 0)).isFalse()
    assertThat(shouldAnimateScrollTo(targetScrollPosition = -10, currentScrollValue = 50)).isFalse()
    assertThat(shouldAnimateScrollTo(targetScrollPosition = 120, currentScrollValue = 120)).isFalse()
  }

  @Test
  fun `shouldAnimateScrollTo returns true when target is non-negative and differs from current`() {
    assertThat(shouldAnimateScrollTo(targetScrollPosition = 0, currentScrollValue = 120)).isTrue()
    assertThat(shouldAnimateScrollTo(targetScrollPosition = 240, currentScrollValue = 0)).isTrue()
  }
}
