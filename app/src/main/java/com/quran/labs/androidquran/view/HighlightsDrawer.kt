package com.quran.labs.androidquran.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.ImageView
import androidx.annotation.ColorInt
import com.quran.labs.androidquran.ui.helpers.AyahHighlight
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.ui.helpers.TransitionAyahHighlight
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import java.util.SortedMap

class HighlightsDrawer(
  private val highlightCoordinates: () -> Map<AyahHighlight, List<AyahBounds>>?,
  private val currentHighlights: () -> SortedMap<HighlightType, Set<AyahHighlight>>?,
) : ImageDrawHelper {

  // cached objects for onDraw
  private val scaledRect = RectF()
  private val alreadyHighlighted = mutableSetOf<AyahHighlight>()

  // Singleton object so we can share the cache across all pages
  private object PaintCache {
    private val cache = mutableMapOf<Int, Paint>()

    fun getPaintForHighlightType(type: HighlightType, @ColorInt color: Int): Paint =
      cache.getOrPut(color) {
        Paint().apply {
          this.color = color
        }
      }
  }

  private fun alreadyHighlightedContains(ayahHighlight: AyahHighlight): Boolean {
    if (ayahHighlight in alreadyHighlighted) {
      return true
    }
    if (ayahHighlight.isTransition) {
      val transitionHighlight = ayahHighlight as TransitionAyahHighlight
      // if x -> y, either x or y is already highlighted, then we don't show the highlight
      return transitionHighlight.source in alreadyHighlighted
          || transitionHighlight.destination in alreadyHighlighted
    }
    return false
  }

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val highlightCoordinates = highlightCoordinates() ?: return
    val currentHighlights = currentHighlights() ?: return
    val matrix = image.imageMatrix

    alreadyHighlighted.clear()

    for ((highlightType, highlights) in currentHighlights.entries) {
      val paint = PaintCache.getPaintForHighlightType(highlightType, highlightType.getColor(image.context))

      for (highlight in highlights) {
        if (alreadyHighlightedContains(highlight)) continue

        val rangesToDraw = highlightCoordinates[highlight]?.map { it.bounds }
        if (rangesToDraw != null && rangesToDraw.isNotEmpty()) {
          for (bounds in rangesToDraw) {
            matrix.mapRect(scaledRect, bounds)
            scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
            canvas.drawRect(scaledRect, paint)
          }

          alreadyHighlighted.add(highlight)
        }
      }
    }
  }

}
