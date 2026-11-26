package com.quran.labs.androidquran.ui.helpers

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import java.util.concurrent.Executors

/**
 * Schedules safe, deferred recycling of exclusively owned page bitmaps after they have been
 * detached from their [android.widget.ImageView].
 *
 * Delaying [Bitmap.recycle] past several VSync frames guarantees that RenderThread has completed
 * any in-flight display list referencing the bitmap, while still reclaiming native pixel buffers
 * and GPU textures promptly during rapid page swiping. Hardware bitmaps (API 28+) are recycled
 * on a lightweight background executor to avoid main-thread gralloc/AHardwareBuffer stalls.
 */
object PageBitmapRecycler {
  @VisibleForTesting
  internal const val RECYCLE_DELAY_MS = 250L

  private val mainHandler = Handler(Looper.getMainLooper())
  private val hardwareRecycleExecutor by lazy {
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "quran-bitmap-recycler").apply { isDaemon = true }
    }
  }

  fun scheduleRecycle(bitmap: Bitmap?, delayMs: Long = RECYCLE_DELAY_MS) {
    if (bitmap == null || bitmap.isRecycled) {
      return
    }
    val offloadHardware =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bitmap.config == Bitmap.Config.HARDWARE
    mainHandler.postDelayed({
      if (!bitmap.isRecycled) {
        if (offloadHardware) {
          hardwareRecycleExecutor.execute {
            if (!bitmap.isRecycled) {
              bitmap.recycle()
            }
          }
        } else {
          bitmap.recycle()
        }
      }
    }, delayMs)
  }
}
