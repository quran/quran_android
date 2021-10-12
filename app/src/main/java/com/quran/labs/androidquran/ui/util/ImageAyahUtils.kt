package com.quran.labs.androidquran.ui.util

import android.graphics.Matrix
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.AyahBounds
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.labs.androidquran.ui.util.ImageAyahUtils
import android.util.SparseArray
import android.graphics.RectF
import android.widget.ImageView
import timber.log.Timber
import com.quran.data.model.selection.SelectedAyahPosition
import com.quran.data.model.selection.SelectedAyahPlacementType
import com.quran.data.model.selection.SelectedAyahPlacementType.BOTTOM
import com.quran.data.model.selection.SelectedAyahPlacementType.TOP
import java.lang.Exception
import java.util.ArrayList

object ImageAyahUtils {
  private fun getAyahFromKey(key: String): SuraAyah? {
    val parts = key.split(":")
    val correctNumberOfParts = parts.size == 2
    val sura = if (correctNumberOfParts) parts[0].toIntOrNull() else null
    val ayah = if (correctNumberOfParts) parts[1].toIntOrNull() else null
    return if (sura != null && ayah != null) {
      SuraAyah(sura, ayah)
    } else {
      null
    }
  }

  fun getAyahFromCoordinates(
    coords: Map<String, List<AyahBounds>>?,
    imageView: HighlightingImageView?,
    xc: Float,
    yc: Float
  ): SuraAyah? {
    if (coords == null || imageView == null) {
      return null
    }

    val pageXY = getPageXY(xc, yc, imageView) ?: return null

    val x = pageXY[0]
    val y = pageXY[1]
    var closestLine = -1
    var closestDelta = -1

    val lineAyahs = SparseArray<MutableList<String>>()
    val keys = coords.keys
    for (key in keys) {
      val bounds = coords[key] ?: continue
      for (b in bounds) {
        // only one AyahBound will exist for an ayah on a particular line
        val line = b.line
        var items = lineAyahs[line]
        if (items == null) {
          items = ArrayList()
        }
        items.add(key)
        lineAyahs.put(line, items)
        val boundsRect = b.bounds
        if (boundsRect.contains(x, y)) {
          return getAyahFromKey(key)
        }
        val delta = Math.min(
          Math.abs(boundsRect.bottom - y).toInt(),
          Math.abs(boundsRect.top - y).toInt()
        )
        if (closestDelta == -1 || delta < closestDelta) {
          closestLine = b.line
          closestDelta = delta
        }
      }
    }

    if (closestLine > -1) {
      var leastDeltaX = -1
      var closestAyah: String? = null
      val ayat: List<String>? = lineAyahs[closestLine]
      if (ayat != null) {
        Timber.d("no exact match, %d candidates.", ayat.size)
        for (ayah in ayat) {
          val bounds = coords[ayah] ?: continue
          for (b in bounds) {
            if (b.line > closestLine) {
              // this is the last ayah in ayat list
              break
            }
            val boundsRect = b.bounds
            if (b.line == closestLine) {
              // if x is within the x of this ayah, that's our answer
              if (boundsRect.right >= x && boundsRect.left <= x) {
                return getAyahFromKey(ayah)
              }

              // otherwise, keep track of the least delta and return it
              val delta = Math.min(
                Math.abs(boundsRect.right - x).toInt(),
                Math.abs(boundsRect.left - x).toInt()
              )
              if (leastDeltaX == -1 || delta < leastDeltaX) {
                closestAyah = ayah
                leastDeltaX = delta
              }
            }
          }
        }
      }

      if (closestAyah != null) {
        Timber.d("fell back to closest ayah of %s", closestAyah)
        return getAyahFromKey(closestAyah)
      }
    }
    return null
  }

  fun getToolBarPosition(
    bounds: List<AyahBounds>, matrix: Matrix,
    screenWidth: Int, screenHeight: Int, toolBarWidth: Int, toolBarHeight: Int
  ): SelectedAyahPosition? {
    var isToolBarUnderAyah = false
    var result: SelectedAyahPosition? = null
    val size = bounds.size
    var chosenRect: RectF
    if (size > 0) {
      val firstRect = RectF()
      var chosen = bounds[0]
      matrix.mapRect(firstRect, chosen.bounds)
      chosenRect = RectF(firstRect)
      var y = firstRect.top - toolBarHeight
      if (y < toolBarHeight) {
        // too close to the top, let's move to the bottom
        chosen = bounds[size - 1]
        matrix.mapRect(chosenRect, chosen.bounds)
        y = chosenRect.bottom
        if (y > screenHeight - toolBarHeight) {
          y = firstRect.bottom
          chosenRect = firstRect
        }
        isToolBarUnderAyah = true
      }
      val midpoint = chosenRect.centerX()
      var x = midpoint - toolBarWidth / 2
      if (x < 0 || x + toolBarWidth > screenWidth) {
        x = chosenRect.left
        if (x + toolBarWidth > screenWidth) {
          x = (screenWidth - toolBarWidth).toFloat()
        }
      }
      val pipPosition = if (isToolBarUnderAyah) TOP else BOTTOM
      result = SelectedAyahPosition(x, y, 0f, 0f, midpoint - x, pipPosition)
    }
    return result
  }

  private fun getPageXY(
    screenX: Float, screenY: Float, imageView: ImageView
  ): FloatArray? {
    if (imageView.drawable == null) {
      return null
    }
    var results: FloatArray? = null
    val inverse = Matrix()
    if (imageView.imageMatrix.invert(inverse)) {
      results = FloatArray(2)
      inverse.mapPoints(results, floatArrayOf(screenX, screenY))
      results[1] = results[1] - imageView.paddingTop
    }
    return results
  }

  fun getYBoundsForHighlight(
    coordinateData: Map<String?, List<AyahBounds>?>, sura: Int, ayah: Int
  ): RectF? {
    val ayahBounds = coordinateData["$sura:$ayah"] ?: return null
    var ayahBoundsRect: RectF? = null
    for (bounds in ayahBounds) {
      if (ayahBoundsRect == null) {
        ayahBoundsRect = bounds.bounds
      } else {
        ayahBoundsRect.union(bounds.bounds)
      }
    }
    return ayahBoundsRect
  }
}
