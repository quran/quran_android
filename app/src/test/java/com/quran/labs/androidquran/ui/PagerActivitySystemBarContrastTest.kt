package com.quran.labs.androidquran.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PagerActivitySystemBarContrastTest {

  @Test
  fun `hidden bars in night page mode use dark bars even when app theme is light`() {
    val lightBars = PagerActivity.shouldUseLightSystemBars(
      isActionBarHidden = true,
      isNightMode = true,
      lightNavigationBar = true
    )
    assertThat(lightBars).isFalse()
  }

  @Test
  fun `hidden bars in day page mode use light bars even when app theme is dark`() {
    val lightBars = PagerActivity.shouldUseLightSystemBars(
      isActionBarHidden = true,
      isNightMode = false,
      lightNavigationBar = false
    )
    assertThat(lightBars).isTrue()
  }

  @Test
  fun `visible bars follow app theme resource regardless of page night mode`() {
    assertThat(
      PagerActivity.shouldUseLightSystemBars(
        isActionBarHidden = false,
        isNightMode = true,
        lightNavigationBar = true
      )
    ).isTrue()

    assertThat(
      PagerActivity.shouldUseLightSystemBars(
        isActionBarHidden = false,
        isNightMode = false,
        lightNavigationBar = false
      )
    ).isFalse()
  }

  @Test
  fun `reading bookmark toast uses bottom inset when action bar is hidden`() {
    val margin = PagerActivity.readingBookmarkToastBottomMargin(
      isActionBarHidden = true,
      isAudioBarVisible = true,
      audioBarHeight = 180,
      bottomInset = 48
    )
    assertThat(margin).isEqualTo(48)
  }

  @Test
  fun `reading bookmark toast uses audio bar height only when chrome and audio bar are visible`() {
    assertThat(
      PagerActivity.readingBookmarkToastBottomMargin(
        isActionBarHidden = false,
        isAudioBarVisible = true,
        audioBarHeight = 180,
        bottomInset = 48
      )
    ).isEqualTo(180)

    assertThat(
      PagerActivity.readingBookmarkToastBottomMargin(
        isActionBarHidden = false,
        isAudioBarVisible = false,
        audioBarHeight = 180,
        bottomInset = 48
      )
    ).isEqualTo(48)
  }
}
