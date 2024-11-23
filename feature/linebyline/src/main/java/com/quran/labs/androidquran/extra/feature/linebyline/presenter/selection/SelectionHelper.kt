package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.SelectionRectangle
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import javax.inject.Inject

class SelectionHelper @Inject constructor() {
  private var currentSelectionPoint: Point? = null
  private var lineCalculation: LineCalculation? = null

  fun setPageDimensions(width: Int, height: Int) {
    lineCalculation = lineCalculation.from(width, height)
  }

  fun startSelection(x: Float, y: Float) {
    currentSelectionPoint = Point(x, y)
  }

  fun selectionRectangle(ayahHighlight: AyahHighlight): SelectionRectangle? {
    val lineCalculation = lineCalculation ?: return null
    val (minY, maxY) = lineCalculation.lineRangeFor(ayahHighlight.lineId)
    val minX = ayahHighlight.left * lineCalculation.width
    val maxX = ayahHighlight.right * lineCalculation.width
    return SelectionRectangle(minX, minY.toFloat(), maxX, maxY.toFloat())
  }

  fun modifySelectionRange(offsetX: Float, offsetY: Float, highlights: List<AyahHighlight>): SuraAyah? {
    val lineCalculation = lineCalculation ?: return null
    val previousPoint = currentSelectionPoint ?: return null
    val currentPoint = previousPoint.withOffset(offsetX, offsetY)

    val lineId = lineCalculation.lineIndexForY(currentPoint.y)
    val result = if (lineId > -1) {
      val matches = highlights
        .filter { it.lineId == lineId && currentPoint.x < (it.right * lineCalculation.width) }
        .map { SuraAyah(it.sura, it.ayah) }
        .toList()

      if (matches.isEmpty()) {
        null
      } else {
        matches.maxOrNull()
      }
    } else {
      null
    }
    currentSelectionPoint = currentPoint
    return result
  }

  fun endSelection() {
    currentSelectionPoint = null
  }

  fun yForLine(lineId: Int): Int {
    return lineCalculation?.lineRangeFor(lineId)?.first ?: 0
  }

  private class Point(val x: Float, val y: Float) {
    fun withOffset(offsetX: Float, offsetY: Float) =
      Point(x + offsetX, y + offsetY)
  }
}
