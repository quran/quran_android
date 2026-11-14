package com.quran.labs.androidquran.feature.reading.presenter.recitation

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.feature.reading.presenter.recitation.PagerActivityRecitationPresenter.Companion.pagesOnScreen
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test

class PagesOnScreenTest {

  private val quranInfo = QuranInfo(MadaniDataSource())

  @Test
  fun `one page`() {
    assertThat(pagesOnScreen(1, isDualPageVisible = false, numberOfPages = 604)).containsExactly(1)
    assertThat(pagesOnScreen(604, isDualPageVisible = false, numberOfPages = 604)).containsExactly(604)
  }

  @Test
  fun `a pair shows the current page and the next`() {
    // what PagerActivity's currentPage gives for the first and the last pair (TabletFragment shows
    // that page and the one after it)
    val first = quranInfo.getPageFromPosition(quranInfo.numberOfPagesConsideringSkipped / 2 - 1, true)
    val last = quranInfo.getPageFromPosition(0, true)
    assertThat(first).isEqualTo(1)
    assertThat(last).isEqualTo(603)

    assertThat(pagesOnScreen(first, isDualPageVisible = true, numberOfPages = 604)).containsExactly(1, 2).inOrder()
    assertThat(pagesOnScreen(301, isDualPageVisible = true, numberOfPages = 604)).containsExactly(301, 302).inOrder()
    assertThat(pagesOnScreen(last, isDualPageVisible = true, numberOfPages = 604)).containsExactly(603, 604).inOrder()
  }

  @Test
  fun `the last pair of an odd number of pages has one page`() {
    assertThat(pagesOnScreen(521, isDualPageVisible = true, numberOfPages = 521)).containsExactly(521)
  }
}
