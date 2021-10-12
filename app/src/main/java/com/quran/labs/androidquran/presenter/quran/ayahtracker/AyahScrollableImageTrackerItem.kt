package com.quran.labs.androidquran.presenter.quran.ayahtracker

import com.quran.data.core.QuranInfo
import com.quran.data.model.selection.SelectedAyahPosition
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.ui.util.ImageAyahUtils
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.labs.androidquran.view.QuranPageLayout
import com.quran.page.common.draw.ImageDrawHelper

class AyahScrollableImageTrackerItem(
  page: Int,
  private val screenHeight: Int,
  quranInfo: QuranInfo,
  quranDisplayData: QuranDisplayData,
  private val quranPageLayout: QuranPageLayout,
  imageDrawHelpers: Set<ImageDrawHelper>,
  highlightingImageView: HighlightingImageView
) : AyahImageTrackerItem(
  page = page,
  screenHeight = screenHeight,
  quranInfo = quranInfo,
  quranDisplayData = quranDisplayData,
  imageDrawHelpers = imageDrawHelpers,
  ayahView = highlightingImageView
) {

  override fun onHighlightAyah(
    page: Int,
    sura: Int,
    ayah: Int,
    type: HighlightType,
    scrollToAyah: Boolean
  ): Boolean {
    if (page == page && scrollToAyah && coordinates != null) {
      val highlightBounds = ImageAyahUtils.getYBoundsForHighlight(coordinates, sura, ayah)
      if (highlightBounds != null) {
        val matrix = ayahView.imageMatrix
        matrix.mapRect(highlightBounds)
        val currentScrollY = quranPageLayout.currentScrollY
        val topOnScreen = highlightBounds.top > currentScrollY &&
            highlightBounds.top < currentScrollY + screenHeight
        val bottomOnScreen = highlightBounds.bottom > currentScrollY &&
            highlightBounds.bottom < currentScrollY + screenHeight
        val encompassesScreen = highlightBounds.top < currentScrollY &&
            highlightBounds.bottom > currentScrollY + screenHeight
        val canEntireAyahBeVisible = highlightBounds.height() < screenHeight

        // scroll when:
        // 1. the entire ayah fits on the screen, but the top or bottom aren't on the screen
        // 2. the entire ayah won't fit on the screen and neither the top is on the screen,
        //    nor is the bottom on the screen, nor is the current ayah greater than the visible
        //    viewport of the ayah (i.e. you're not in the middle of the ayah right now).
        val scroll = canEntireAyahBeVisible && (!topOnScreen || !bottomOnScreen) ||
            !canEntireAyahBeVisible && !topOnScreen && !bottomOnScreen && !encompassesScreen
        if (scroll) {
          val y = highlightBounds.top.toInt() - (0.05 * screenHeight).toInt()
          quranPageLayout.smoothScrollLayoutTo(y)
        }
      }
    }
    return super.onHighlightAyah(page, sura, ayah, type, scrollToAyah)
  }

  override fun getToolBarPosition(
    page: Int, sura: Int, ayah: Int, toolBarWidth: Int, toolBarHeight: Int
  ): SelectedAyahPosition? {
    val position = super.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight)
    return position?.withYScroll(-quranPageLayout.currentScrollY.toFloat())
  }
}
