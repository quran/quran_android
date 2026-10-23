package com.quran.labs.androidquran.ui.util

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test

class VolumeKeyNavigationTest {

  private val quranInfo = QuranInfo(MadaniDataSource())

  /** The page shown after pressing [keyCode] on [page], mapped through the real pager positions. */
  private fun pageAfterPressing(keyCode: Int, swapped: Boolean, page: Int = 50): Int {
    val position = quranInfo.getPositionFromPage(page, false)
    val offset = VolumeKeyNavigation.pagerOffset(keyCode, swapped)
    return quranInfo.getPageFromPosition(position + offset, false)
  }

  @Test
  fun `volume down goes to the next page by default`() {
    assertThat(pageAfterPressing(KeyEvent.KEYCODE_VOLUME_DOWN, swapped = false)).isEqualTo(51)
  }

  @Test
  fun `volume up goes to the previous page by default`() {
    assertThat(pageAfterPressing(KeyEvent.KEYCODE_VOLUME_UP, swapped = false)).isEqualTo(49)
  }

  @Test
  fun `volume up goes to the next page when swapped`() {
    assertThat(pageAfterPressing(KeyEvent.KEYCODE_VOLUME_UP, swapped = true)).isEqualTo(51)
  }

  @Test
  fun `volume down goes to the previous page when swapped`() {
    assertThat(pageAfterPressing(KeyEvent.KEYCODE_VOLUME_DOWN, swapped = true)).isEqualTo(49)
  }

  @Test
  fun `other keys do not move the pager`() {
    assertThat(VolumeKeyNavigation.pagerOffset(KeyEvent.KEYCODE_PAGE_DOWN, swapped = false)).isEqualTo(0)
    assertThat(VolumeKeyNavigation.pagerOffset(KeyEvent.KEYCODE_PAGE_DOWN, swapped = true)).isEqualTo(0)
    assertThat(VolumeKeyNavigation.pagerOffset(KeyEvent.KEYCODE_VOLUME_MUTE, swapped = true)).isEqualTo(0)
  }
}
