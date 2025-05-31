package com.quran.labs.androidquran.common.drawing.helper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.ImageView
import com.quran.labs.androidquran.common.drawing.AyahMarkerDrawable
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import java.text.NumberFormat
import java.util.Locale

class AyahMarkerDrawer(private val drawable: AyahMarkerDrawable) : ImageDrawHelper {
  private val formatter = NumberFormat.getIntegerInstance(Locale("ar", "EG"))

  private val textRatio = 0.0375f
  private val largeTextRatio = 0.03f
  private val points = floatArrayOf(0.0f, 0.0f)
  private val rect = RectF()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG or
      Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    color = Color.BLACK
    textAlign = Paint.Align.CENTER
  }

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val imageDrawable = image.drawable
    if (imageDrawable != null) {
      val imageMatrix = image.imageMatrix
      rect.set(0f,
          0f,
          imageDrawable.intrinsicWidth.toFloat(),
          imageDrawable.intrinsicHeight.toFloat())
      imageMatrix.mapRect(rect)

      val width = rect.width()
      val markerDimen = (0.025f * 2 * width).toInt()
      drawable.bounds.set(0, 0, markerDimen, markerDimen)

      pageCoordinates.ayahMarkers
          .forEach {
            points[0] = it.x.toFloat()
            points[1] = it.y.toFloat()
            imageMatrix.mapPoints(points)

            val x = points[0] - (markerDimen / 2) + image.paddingLeft
            val y = points[1] - (markerDimen / 2) + image.paddingTop
            canvas.translate(x, y)
            drawable.draw(canvas)
            canvas.translate(-x, -y)

            paint.textSize = width * if (it.ayah > 100) largeTextRatio else textRatio
            val yOffset = (paint.descent() - paint.ascent()) / 2 - paint.descent()
            canvas.drawText(formatter.format(it.ayah),
                points[0] + image.paddingLeft,
                points[1] + yOffset + image.paddingTop + 4,
                paint)
          }
    }
  }
}
