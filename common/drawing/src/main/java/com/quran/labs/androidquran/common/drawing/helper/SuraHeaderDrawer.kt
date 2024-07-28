package com.quran.labs.androidquran.common.drawing.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper

class SuraHeaderDrawer(@DrawableRes val headerResource: Int,
                       @ColorInt val headerColor: Int) : ImageDrawHelper {
  private val paint = Paint().apply {
    colorFilter = PorterDuffColorFilter(headerColor, PorterDuff.Mode.SRC_IN)
    isDither = true
    isFilterBitmap = true
  }
  private val bounds = RectF()
  private lateinit var header: Bitmap

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val width = image.width
    if (width > 0) {
      pageCoordinates.suraHeaders.forEach {
        if (!::header.isInitialized) {
          val tmp = BitmapFactory.decodeResource(image.context.resources, headerResource,
              BitmapFactory.Options().apply { inSampleSize = 2 })
          header = tmp.extractAlpha()
          tmp.recycle()
        }

        val intrinsicWidth = image.drawable.intrinsicWidth
        val left = intrinsicWidth * 0.025f
        val height = intrinsicWidth * 0.1f
        val top = it.y - height / 2
        bounds.set(left,
            top,
            (intrinsicWidth - left),
            (top + height))
        image.imageMatrix.mapRect(bounds)
        bounds.top += image.paddingTop
        bounds.bottom += image.paddingTop
        bounds.left += image.paddingLeft
        bounds.right += image.paddingRight
        canvas.drawBitmap(header, null, bounds, paint)
      }
    }
  }
}
