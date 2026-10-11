package com.quran.labs.androidquran.ui.helpers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Config(application = Application::class, sdk = [33], qualifiers = "en")
@RunWith(RobolectricTestRunner::class)
class QuranDisplayHelperTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val quranInfo = QuranInfo(MadaniDataSource())

  @Test
  fun `quarters before juz eight appear on the spread containing the marker`() {
    assertPopup(133, isDualPageVisible = true, expected = "¼ Hizb 14")
    assertPopup(139, isDualPageVisible = true, expected = "¾ Hizb 14")
  }

  @Test
  fun `juz eight appears when page 142 is visible`() {
    assertPopup(142, isDualPageVisible = true, expected = "Juz' 8")
  }

  @Test
  fun `spread after juz eight shows its own marker`() {
    assertPopup(144, isDualPageVisible = true, expected = "¼ Hizb 15")
  }

  @Test
  fun `marker on the first visible page is still shown`() {
    assertPopup(137, isDualPageVisible = true, expected = "½ Hizb 14")
  }

  @Test
  fun `spread without markers does not show the previous spread's marker`() {
    assertPopup(149, isDualPageVisible = true, expected = null)
  }

  @Test
  fun `single page and Arabic translation split only show the current page's marker`() {
    assertPopup(141, isDualPageVisible = false, expected = null)
    assertPopup(142, isDualPageVisible = false, expected = "Juz' 8")
    assertPopup(143, isDualPageVisible = false, expected = null)
    assertPopup(144, isDualPageVisible = false, expected = "¼ Hizb 15")
  }

  @Test
  fun `a recent popup suppresses markers on both visible pages`() {
    val lastPopupTime = System.currentTimeMillis()

    for (page in listOf(137, 141)) {
      val result = QuranDisplayHelper.displayMarkerPopup(
        context, quranInfo, page, true, lastPopupTime
      )
      assertThat(result).isEqualTo(lastPopupTime)
    }
    assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
  }

  private fun assertPopup(page: Int, isDualPageVisible: Boolean, expected: String?) {
    ShadowToast.reset()
    val position = quranInfo.getPositionFromPage(page, isDualPageVisible)
    val firstVisiblePage = quranInfo.getPageFromPosition(position, isDualPageVisible)

    val lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
      context, quranInfo, firstVisiblePage, isDualPageVisible, 0L
    )

    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(expected)
    assertThat(ShadowToast.shownToastCount()).isEqualTo(if (expected == null) 0 else 1)
    if (expected == null) {
      assertThat(lastPopupTime).isEqualTo(0L)
    } else {
      assertThat(lastPopupTime).isGreaterThan(0L)
    }
  }
}
