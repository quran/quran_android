package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode.BACKGROUND
import com.quran.data.model.highlight.HighlightType.Mode.COLOR
import com.quran.data.model.highlight.HighlightType.Mode.HIDE
import com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLINE
import com.quran.data.model.highlight.HighlightType.Mode.values
import com.quran.labs.androidquran.ui.helpers.AyahHighlight
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.TransitionAyahHighlight
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import java.util.SortedMap

class HighlightsDrawer(
  private val ayahCoordinates: () -> AyahCoordinates?,
  private val highlightCoordinates: () -> Map<AyahHighlight, List<AyahBounds>>?,
  private val currentHighlights: () -> SortedMap<HighlightType, Set<AyahHighlight>>?,
  private val superOnDraw: (Canvas) -> Unit,
  private vararg val highlightTypesFilter: HighlightType.Mode = values(),
) : ImageDrawHelper {

  // cached objects for onDraw
  private val scaledRect = RectF()
  private val alreadyHighlighted = mutableMapOf<HighlightType.Mode, MutableSet<AyahHighlight>>()

  // Singleton object so we can share the cache across all pages
  private object PaintCache {
    private val cache = mutableMapOf<Pair<HighlightType.Mode, @ColorInt Int>, Paint>()

    fun getPaintForHighlightType(context: Context, type: HighlightType): Paint =
      cache.getOrPut(Pair(type.mode, type.colorResId)) {
        Paint().apply {
          val color = ContextCompat.getColor(context, type.colorResId)
          when (type.mode) {
            COLOR -> this.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            else -> this.color = color
          }
        }
      }
  }

  private fun alreadyHighlightedContains(
    highlightType: HighlightType,
    highlight: AyahHighlight
  ): Boolean {
    val alreadyHighlighted = alreadyHighlighted.getOrElse(highlightType.mode) { emptySet() }
    if (highlight in alreadyHighlighted) {
      return true
    }
    if (highlight.isTransition()) {
      val transitionHighlight = highlight as TransitionAyahHighlight
      // if x -> y, either x or y is already highlighted, then we don't show the highlight
      return transitionHighlight.source in alreadyHighlighted
          || transitionHighlight.destination in alreadyHighlighted
    }
    return false
  }

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val highlightCoordinates = highlightCoordinates() ?: return
    val glyphsCoords = ayahCoordinates()?.glyphCoordinates
    val currentHighlights = currentHighlights() ?: return

    alreadyHighlighted.clear()

    val filteredHighlights = currentHighlights.filterKeys { it.mode in highlightTypesFilter }

    for ((highlightType, highlights) in filteredHighlights) {
      val paint = PaintCache.getPaintForHighlightType(image.context, highlightType)

      for (highlight in highlights) {
        if (alreadyHighlightedContains(highlightType, highlight)) continue

        val rangesToDraw = glyphsCoords?.takeIf { !highlightType.isTransitionAnimated }
          ?.getBounds(highlight.key, true, highlightType.mode == BACKGROUND)
          ?: highlightCoordinates[highlight]?.map { it.bounds }

        if (rangesToDraw != null && rangesToDraw.isNotEmpty()) {
          for (bounds in rangesToDraw) {
            if (highlightType.mode == UNDERLINE) {
              val underlineThickness = TypedValue.applyDimension(
                COMPLEX_UNIT_DIP, UNDERLINE_THICKNESS_DIPS, image.resources.displayMetrics)
              bounds.top = bounds.bottom
              bounds.bottom += underlineThickness
            }

            image.imageMatrix.mapRect(scaledRect, bounds)
            scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())

            when (highlightType.mode) {
              HIDE -> {
                // Clip out the bounds to be hidden so they don't get drawn
                clipOutRect(canvas, scaledRect)
              }

              COLOR -> {
                // 1. save previously applied clips, and clip to just the bounds we want to colorize
                canvas.save()
                canvas.clipRect(scaledRect)
                // 2. save previously applied color filter, and draw this clipped part with the color we want
                val appliedFilter = image.colorFilter
                image.colorFilter = paint.colorFilter
                superOnDraw(canvas)
                // 3. restore sate (previously applied color filter + previous canvas clips)
                image.colorFilter = appliedFilter
                canvas.restore()
                // 4. clip out the bounds we just colorized so it doesn't get drawn again
                clipOutRect(canvas, scaledRect)
              }

              HIGHLIGHT, BACKGROUND, UNDERLINE -> {
                // Draw a rectangle with the specified paint
                canvas.drawRect(scaledRect, paint)
              }
            }
          }

          alreadyHighlighted.getOrPut(highlightType.mode, { mutableSetOf() }).add(highlight)
        }
      }
    }
  }

  private fun clipOutRect(canvas: Canvas, rect: RectF) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      canvas.clipOutRect(rect)
    } else {
      canvas.clipRect(rect, Region.Op.DIFFERENCE)
    }
  }

  companion object {
    private const val UNDERLINE_THICKNESS_DIPS = 3f // dp
  }
}
