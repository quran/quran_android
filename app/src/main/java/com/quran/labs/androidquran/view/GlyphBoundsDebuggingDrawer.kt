package com.quran.labs.androidquran.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.ImageView
import androidx.annotation.ColorInt
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper

class GlyphBoundsDebuggingDrawer(
  private val ayahCoordinates: () -> AyahCoordinates?,
) : ImageDrawHelper {

  private val debugExpandedLines =  true
  private val debugLines =          false
  private val debugExpandedGlyphs = true
  private val debugGlyphs =         true

  private val gray =    0xFFCCCCCC.toInt()
  private val yellow =  0xFFFFC107.toInt()
  private val green =   0xFF4CAF50.toInt()

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val coords = ayahCoordinates()?.glyphCoordinates ?: return

    if (debugExpandedLines) coords.expandedLineBounds.values
      .drawBounds(canvas, image, gray)

    if (debugLines) coords.lineBounds.values
      .drawBounds(canvas, image, yellow)

    if (debugExpandedGlyphs) coords.expandedGlyphs.map { it.bounds }
      .drawBounds(canvas, image, yellow)

    if (debugGlyphs) coords.glyphsByLine.values.flatten().map { it.bounds }
      .drawBounds(canvas, image, green)
  }

  private fun Collection<RectF>.drawBounds(canvas: Canvas, image: ImageView, color: Int) =
    drawBounds(canvas, image, colors = intArrayOf(color, darkenColor(color)))

  private fun Collection<RectF>.drawBounds(
    canvas: Canvas,
    image: ImageView,
    alpha: Float = 0.5f,
    vararg colors: Int = intArrayOf(yellow, darkenColor(yellow)),
  ) {
    val scaledRect = RectF()
    val paints = colors.map { Paint().apply { color = it; this.alpha = (255 * alpha).toInt() } }
    this.forEachIndexed { idx, bounds ->
      image.imageMatrix.mapRect(scaledRect, bounds)
      scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
      canvas.drawRect(scaledRect, paints[idx % paints.size])
    }
  }

  private fun darkenColor(@ColorInt color: Int, factor: Float = 0.7f): Int {
    val alpha = Color.alpha(color)
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] = hsv[2] * factor
    return Color.HSVToColor(alpha, hsv)
  }
}
