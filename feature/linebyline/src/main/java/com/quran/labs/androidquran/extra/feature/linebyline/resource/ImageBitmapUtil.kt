package com.quran.labs.androidquran.extra.feature.linebyline.resource

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.common.drawing.util.getDrawableAsAlpha8
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
class ImageBitmapUtil @Inject constructor() {
  private val imageCache = ConcurrentHashMap<Int, ImageBitmap>()
  private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()

  init {
    BitmapPoolTrimRegistry.register { clearCache() }
  }

  fun getCachedAlpha8Image(@DrawableRes drawableResId: Int): ImageBitmap? = imageCache[drawableResId]

  fun getCachedAlpha8Bitmap(@DrawableRes drawableResId: Int): Bitmap? = bitmapCache[drawableResId]

  suspend fun alpha8ImageAsync(appContext: Context, @DrawableRes drawableResId: Int): ImageBitmap {
    imageCache[drawableResId]?.let { return it }
    return withContext(Dispatchers.IO) {
      alpha8Image(appContext, drawableResId)
    }
  }

  suspend fun alpha8BitmapAsync(appContext: Context, @DrawableRes drawableResId: Int): Bitmap {
    bitmapCache[drawableResId]?.let { return it }
    return withContext(Dispatchers.IO) {
      alpha8Bitmap(appContext, drawableResId)
    }
  }

  fun alpha8Image(appContext: Context, @DrawableRes drawableResId: Int): ImageBitmap {
    return imageCache[drawableResId] ?: synchronized(imageCache) {
      imageCache.getOrPut(drawableResId) {
        alpha8Bitmap(appContext, drawableResId).asImageBitmap()
      }
    }
  }

  fun alpha8Bitmap(appContext: Context, @DrawableRes drawableResId: Int): Bitmap {
    return bitmapCache[drawableResId] ?: synchronized(bitmapCache) {
      bitmapCache.getOrPut(drawableResId) {
        appContext.resources.getDrawableAsAlpha8(drawableResId)
      }
    }
  }

  fun clearCache() {
    val toRecycle = ArrayList<Bitmap>()
    synchronized(imageCache) {
      imageCache.clear()
    }
    synchronized(bitmapCache) {
      toRecycle.addAll(bitmapCache.values)
      bitmapCache.clear()
    }
    toRecycle.forEach { LineBitmapRecycler.scheduleRecycle(it) }
  }
}
