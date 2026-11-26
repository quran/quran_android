package com.quran.labs.androidquran.common.drawing.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.SparseArray
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

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    val width = image.width
    val headers = pageCoordinates.suraHeaders
    val imageDrawable = image.drawable
    if (width > 0 && imageDrawable != null && headers.isNotEmpty()) {
      val header = getOrLoadHeader(image.context)
      val intrinsicWidth = imageDrawable.intrinsicWidth
      val left = intrinsicWidth * 0.025f
      val height = intrinsicWidth * 0.1f

      headers.forEach {
        val top = it.y - height / 2
        bounds.set(left,
            top,
            (intrinsicWidth - left),
            (top + height))
        image.imageMatrix.mapRect(bounds)
        bounds.top += image.paddingTop
        bounds.bottom += image.paddingTop
        bounds.left += image.paddingLeft
        bounds.right += image.paddingLeft
        canvas.drawBitmap(header, null, bounds, paint)
      }
    }
  }

  private fun getOrLoadHeader(context: Context): Bitmap {
    synchronized(headerCache) {
      val cached = headerCache[headerResource]
      if (cached != null && !cached.isRecycled) {
        return cached
      }
      val tmp = BitmapFactory.decodeResource(
        context.resources,
        headerResource,
        BitmapFactory.Options().apply { inSampleSize = 2 }
      )
      val alphaBitmap = if (tmp.config == Bitmap.Config.ALPHA_8) {
        tmp
      } else {
        tmp.extractAlpha().also { tmp.recycle() }
      }
      headerCache.put(headerResource, alphaBitmap)
      return alphaBitmap
    }
  }

  companion object {
    private val headerCache = SparseArray<Bitmap>()

    @JvmStatic
    fun clearCache() {
      synchronized(headerCache) {
        headerCache.clear()
      }
    }
  }
}
