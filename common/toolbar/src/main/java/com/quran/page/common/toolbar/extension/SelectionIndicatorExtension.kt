package com.quran.page.common.toolbar.extension

import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionIndicator.None
import com.quran.data.model.selection.SelectionIndicator.SelectedItemPosition
import com.quran.data.model.selection.SelectionIndicator.SelectedPointPosition
import com.quran.page.common.toolbar.dao.SelectedAyahPlacementType
import com.quran.page.common.toolbar.dao.SelectionIndicatorPosition

fun SelectionIndicator.toInternalPosition(
  width: Int,
  height: Int,
  toolBarWidth: Int,
  toolBarHeight: Int
): SelectionIndicatorPosition? {
  return when (this) {
    None, SelectionIndicator.ScrollOnly -> null
    is SelectedItemPosition -> toInternalPosition(width, height, toolBarWidth, toolBarHeight)
    is SelectedPointPosition -> SelectionIndicatorPosition(
      this.x + this.xScroll,
      this.y + this.yScroll,
      0f,
      SelectedAyahPlacementType.TOP
    )
  }
}

private fun SelectedItemPosition.toInternalPosition(
  width: Int,
  height: Int,
  toolBarWidth: Int,
  toolBarHeight: Int
): SelectionIndicatorPosition {
  var chosen = firstItem
  var y = firstItem.top - toolBarHeight
  var isToolBarUnderAyah = false

  if (y < toolBarHeight) {
    // too close to the top, let's move to the bottom
    chosen = lastItem
    y = lastItem.bottom
    if (y > (height - toolBarHeight)) {
      chosen = firstItem
      y = firstItem.bottom
    }
    isToolBarUnderAyah = true
  }

  val midpoint = chosen.centerX()
  var x = midpoint - (toolBarWidth / 2)
  if (x < 0 || x + toolBarWidth > width) {
    x = chosen.left
    if (x + toolBarWidth > width) {
      x = (width - toolBarWidth).toFloat()
    }
  }

  y += yScroll

  val position =
    if (isToolBarUnderAyah) SelectedAyahPlacementType.TOP else SelectedAyahPlacementType.BOTTOM
  // pip is offset from the box, so xScroll is only added to starting x point and not pipOffset
  return SelectionIndicatorPosition(x + xScroll, y, midpoint - x, position)
}
