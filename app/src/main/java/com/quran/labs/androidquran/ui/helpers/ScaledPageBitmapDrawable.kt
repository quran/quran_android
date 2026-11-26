package com.quran.labs.androidquran.ui.helpers

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable

/**
 * [BitmapDrawable] that reports the original unscaled page image dimensions ([logicalWidth] x
 * [logicalHeight]) as its intrinsic size while rendering a viewport-matched [Bitmap].
 *
 * Preserving logical intrinsic dimensions guarantees that [android.widget.ImageView]'s
 * `imageMatrix` stays identical to the full-resolution PNG coordinate space (`ayahinfo_*.db`),
 * keeping ayah highlights, touch hit-testing, sura headers, and verse markers pixel-exact.
 */
class ScaledPageBitmapDrawable(
  resources: Resources,
  bitmap: Bitmap,
  private val logicalWidth: Int,
  private val logicalHeight: Int
) : BitmapDrawable(resources, bitmap) {

  init {
    isFilterBitmap = true
  }

  override fun getIntrinsicWidth(): Int = logicalWidth

  override fun getIntrinsicHeight(): Int = logicalHeight

  companion object {
    fun create(
      resources: Resources,
      bitmap: Bitmap,
      sourceWidth: Int,
      sourceHeight: Int
    ): BitmapDrawable {
      return if (
        sourceWidth > 0 &&
        sourceHeight > 0 &&
        (sourceWidth != bitmap.width || sourceHeight != bitmap.height)
      ) {
        ScaledPageBitmapDrawable(resources, bitmap, sourceWidth, sourceHeight)
      } else {
        BitmapDrawable(resources, bitmap)
      }
    }
  }
}
