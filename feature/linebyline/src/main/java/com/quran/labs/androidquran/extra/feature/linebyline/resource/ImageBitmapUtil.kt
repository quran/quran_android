package com.quran.labs.androidquran.extra.feature.linebyline.resource

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.quran.labs.androidquran.common.drawing.util.getDrawableAsAlpha8
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageBitmapUtil @Inject constructor() {
  private val cache = mutableMapOf<Int, ImageBitmap>()

  fun alpha8Image(appContext: Context, @DrawableRes drawableResId: Int): ImageBitmap {
    return cache.getOrPut(drawableResId) {
      appContext.resources.getDrawableAsAlpha8(drawableResId).asImageBitmap()
    }
  }
}
