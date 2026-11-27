package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.util.LongSparseArray
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode.BACKGROUND
import com.quran.data.model.highlight.HighlightType.Mode.COLOR
import com.quran.data.model.highlight.HighlightType.Mode.HIDE
import com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLINE
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.ui.helpers.AyahHighlight
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.TransitionAyahHighlight
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import java.util.EnumSet
import java.util.SortedMap

class HighlightsDrawer(
  private val ayahCoordinates: () -> AyahCoordinates?,
  private val highlightCoordinates: () -> Map<AyahHighlight, List<AyahBounds>>?,
  private val currentHighlights: () -> SortedMap<HighlightType, Set<AyahHighlight>>?,
  private val superOnDraw: (Canvas) -> Unit,
  private val isNightMode: () -> Boolean = { false },
  private vararg val highlightTypesFilter: HighlightType.Mode = HighlightType.Mode.entries.toTypedArray(),
) : ImageDrawHelper {

  private val allowedModes: EnumSet<HighlightType.Mode> =
    EnumSet.noneOf(HighlightType.Mode::class.java).apply { addAll(highlightTypesFilter) }

  // cached objects for onDraw
  private val scaledRect = RectF()
  private val drawBounds = RectF()
  private val alreadyHighlighted = mutableMapOf<HighlightType.Mode, MutableSet<AyahHighlight>>()
  private var cachedDensity = -1f
  private var cachedUnderlineThickness = 0f

  // Singleton object so we can share the cache across all pages
  private object PaintCache {
    private val cache = LongSparseArray<Paint>()

    fun getPaintForHighlightType(
      context: Context,
      type: HighlightType,
      isNightMode: Boolean
    ): Paint {
      if (type == HighlightTypes.SELECTION) {
        val style = AppThemeStyle.current
        val key = (1L shl 60) or
          (style.ordinal.toLong() shl 1) or
          (if (isNightMode) 1L else 0L)
        val cached = cache.get(key)
        if (cached != null) return cached
        return Paint().apply {
          color = style.palette(isNightMode).ayahHighlightFill.toArgb()
        }.also { cache.put(key, it) }
      }
      val colorResId = if (isNightMode) type.nightColorResId else type.colorResId
      val key = (type.mode.ordinal.toLong() shl 33) or
        ((colorResId.toLong() and 0xFFFFFFFFL) shl 1) or
        (if (isNightMode) 1L else 0L)
      val cached = cache.get(key)
      if (cached != null) return cached

      return Paint().apply {
        val color = ContextCompat.getColor(context, colorResId)
        when (type.mode) {
          COLOR -> this.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
          UNDERLAY -> this.color = if (isNightMode) {
            ColorUtils.setAlphaComponent(color, NIGHT_UNDERLAY_ALPHA)
          } else {
            color
          }
          else -> this.color = color
        }
      }.also { cache.put(key, it) }
    }
  }

  private fun alreadyHighlightedContains(
    highlightType: HighlightType,
    highlight: AyahHighlight
  ): Boolean {
    val alreadyHighlighted = alreadyHighlighted[highlightType.mode] ?: return false
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
    val currentHighlights = currentHighlights() ?: return
    if (currentHighlights.isEmpty()) return
    val highlightCoordinates = highlightCoordinates() ?: return
    val coords = ayahCoordinates()
    val glyphsCoords = coords?.glyphCoordinates
    val ayahLineCoords = coords?.ayahLineCoordinates
    val underlineRaiseRatio = coords?.underlineRaiseRatio ?: 0f

    val metrics = image.resources.displayMetrics
    if (cachedDensity != metrics.density) {
      cachedDensity = metrics.density
      cachedUnderlineThickness = TypedValue.applyDimension(
        COMPLEX_UNIT_DIP, UNDERLINE_THICKNESS_DIPS, metrics
      )
    }
    val underlineThickness = cachedUnderlineThickness
    val nightMode = isNightMode()

    alreadyHighlighted.values.forEach { it.clear() }

    for ((highlightType, highlights) in currentHighlights) {
      if (highlightType.mode !in allowedModes || highlights.isEmpty()) continue
      val paint = PaintCache.getPaintForHighlightType(image.context, highlightType, nightMode)
      val isUnderline = highlightType.mode == UNDERLINE

      for (highlight in highlights) {
        if (alreadyHighlightedContains(highlightType, highlight)) continue

        val rectRanges = glyphsCoords?.takeIf { !highlightType.isTransitionAnimated }
          ?.getBounds(highlight.key, !isUnderline, highlightType.mode == BACKGROUND)

        var drew = false
        if (rectRanges != null) {
          if (rectRanges.isNotEmpty()) {
            for (i in rectRanges.indices) {
              drawSingleBounds(
                rectRanges[i],
                isUnderline,
                underlineRaiseRatio,
                underlineThickness,
                highlightType,
                paint,
                canvas,
                image
              )
            }
            drew = true
          }
        } else {
          val ayahBoundsList = (if (isUnderline) ayahLineCoords?.get(highlight.key) else null)
            ?: highlightCoordinates[highlight]
          if (ayahBoundsList != null && ayahBoundsList.isNotEmpty()) {
            for (i in ayahBoundsList.indices) {
              drawSingleBounds(
                ayahBoundsList[i].bounds,
                isUnderline,
                underlineRaiseRatio,
                underlineThickness,
                highlightType,
                paint,
                canvas,
                image
              )
            }
            drew = true
          }
        }

        if (drew) {
          alreadyHighlighted.getOrPut(highlightType.mode) { mutableSetOf() }.add(highlight)
        }
      }
    }
  }

  private fun drawSingleBounds(
    bounds: RectF,
    isUnderline: Boolean,
    underlineRaiseRatio: Float,
    underlineThickness: Float,
    highlightType: HighlightType,
    paint: Paint,
    canvas: Canvas,
    image: ImageView
  ) {
    val rectToMap = if (isUnderline) {
      drawBounds.set(bounds)
      drawBounds.top = drawBounds.bottom - drawBounds.height() * underlineRaiseRatio
      drawBounds.bottom = drawBounds.top + underlineThickness
      drawBounds
    } else {
      bounds
    }

    image.imageMatrix.mapRect(scaledRect, rectToMap)
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

      UNDERLAY, HIGHLIGHT, BACKGROUND, UNDERLINE -> {
        // Draw a rectangle with the specified paint
        canvas.drawRect(scaledRect, paint)
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
    private const val NIGHT_UNDERLAY_ALPHA = 0x66
  }
}
