package com.quran.labs.androidquran.extra.feature.linebyline.ui

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranPageCalculator
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

  @Test
  fun `shouldRequestLayoutOnPageInfoUpdate skips requestLayout for portrait bitmap arrival`() {
    assertThat(
      shouldRequestLayoutOnPageInfoUpdate(
        isScrollable = false,
        hasMeasurements = true,
        prevShowSidelines = false,
        nextShowSidelines = false,
        prevHadBitmap = false,
        nowHasBitmap = true
      )
    ).isFalse()

    assertThat(
      shouldRequestLayoutOnPageInfoUpdate(
        isScrollable = true,
        hasMeasurements = true,
        prevShowSidelines = false,
        nextShowSidelines = false,
        prevHadBitmap = false,
        nowHasBitmap = true
      )
    ).isTrue()

    assertThat(
      shouldRequestLayoutOnPageInfoUpdate(
        isScrollable = false,
        hasMeasurements = true,
        prevShowSidelines = false,
        nextShowSidelines = true,
        prevHadBitmap = true,
        nowHasBitmap = true
      )
    ).isTrue()
  }

  @Test
  fun `QuranPageCalculator caches Measurements for identical dimensions`() {
    val calc = QuranPageCalculator()
    val first = calc.calculate(720, 1420)
    val second = calc.calculate(720, 1420)
    assertThat(second).isSameInstanceAs(first)
  }
}
