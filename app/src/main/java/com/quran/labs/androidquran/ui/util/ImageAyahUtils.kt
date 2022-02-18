package com.quran.labs.androidquran.ui.util

import android.graphics.Matrix
import android.graphics.RectF
import android.util.SparseArray
import android.widget.ImageView
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.page.common.data.AyahBounds
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.min

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

  fun getAyahFromCoordinates(coords: Map<String, List<AyahBounds>>?,
                             imageView: HighlightingImageView?, xc: Float, yc: Float): SuraAyah? {
    return getAyahBoundsFromCoordinates(coords, imageView, xc, yc)?.first
  }

  private fun getAyahBoundsFromCoordinates(
      coords: Map<String, List<AyahBounds>>?,
      imageView: HighlightingImageView?,
      xc: Float,
      yc: Float
  ): Pair<SuraAyah?, AyahBounds>? {
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
          return Pair(getAyahFromKey(key), b)
        }
        val delta = min(
          abs(boundsRect.bottom - y).toInt(),
          abs(boundsRect.top - y).toInt()
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
      var closestAyahBounds: AyahBounds? = null
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
                return Pair(getAyahFromKey(ayah), b)
              }

              // otherwise, keep track of the least delta and return it
              val delta = min(
                abs(boundsRect.right - x).toInt(),
                abs(boundsRect.left - x).toInt()
              )
              if (leastDeltaX == -1 || delta < leastDeltaX) {
                closestAyah = ayah
                closestAyahBounds = b
                leastDeltaX = delta
              }
            }
          }
        }
      }

      if (closestAyah != null && closestAyahBounds != null) {
        Timber.d("fell back to closest ayah of %s", closestAyah)
        return Pair(getAyahFromKey(closestAyah), closestAyahBounds)
      }
    }
    return null
  }

  fun getToolBarPosition(
    bounds: List<AyahBounds>,
    matrix: Matrix,
    xPadding: Int,
    yPadding: Int
  ): SelectionIndicator {

    return if (bounds.isNotEmpty()) {
      val first = bounds.first()
      val last = bounds.last()

      val mappedRect = RectF()
      matrix.mapRect(mappedRect, first.bounds)
      val top = SelectionRectangle(
        mappedRect.left + xPadding,
        mappedRect.top + yPadding,
        mappedRect.right + xPadding,
        mappedRect.bottom + yPadding
      )
      val bottom = if (first === last) { top }
      else {
        matrix.mapRect(mappedRect, last.bounds)
        SelectionRectangle(
          mappedRect.left + xPadding,
          mappedRect.top + yPadding,
          mappedRect.right + xPadding,
          mappedRect.bottom + yPadding
        )
      }
      SelectionIndicator.SelectedItemPosition(top, bottom)
    } else {
      SelectionIndicator.None
    }
  }

  fun getPageXY(
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
    coordinateData: Map<String, List<AyahBounds>?>, sura: Int, ayah: Int
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
