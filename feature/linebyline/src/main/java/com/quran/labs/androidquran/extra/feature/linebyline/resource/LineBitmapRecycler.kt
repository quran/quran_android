package com.quran.labs.androidquran.extra.feature.linebyline.resource

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import java.util.IdentityHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Schedules safe, deferred recycling of line-by-line [Bitmap]s after the page view detaches.
 *
 * Delaying [Bitmap.recycle] past several VSync frames off the Main Looper guarantees RenderThread
 * has completed any in-flight display list referencing the bitmap without waking the UI thread
 * mid-fling. Call [recycleNow] when leaving the reader cleanly to release GL/EGL textures
 * synchronously before the next activity launches.
 */
object LineBitmapRecycler {
  private const val RECYCLE_DELAY_MS = 250L

  private val pendingLock = Any()
  private val pendingFutures = IdentityHashMap<Bitmap, ScheduledFuture<*>>()

  private val recycleScheduler: ScheduledExecutorService by lazy {
    Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "quran-line-bitmap-recycler").apply { isDaemon = true }
    }
  }

  fun scheduleRecycle(bitmap: Bitmap?, delayMs: Long = RECYCLE_DELAY_MS) {
    if (bitmap == null || bitmap.isRecycled) {
      return
    }
    if (delayMs <= 0L) {
      synchronized(pendingLock) {
        pendingFutures.remove(bitmap)?.cancel(false)
      }
      recycleScheduler.execute {
        if (!bitmap.isRecycled) {
          runCatching { bitmap.recycle() }
        }
      }
      return
    }
    synchronized(pendingLock) {
      pendingFutures.remove(bitmap)?.cancel(false)
      var scheduled: ScheduledFuture<*>? = null
      scheduled = recycleScheduler.schedule(
        {
          synchronized(pendingLock) {
            if (pendingFutures[bitmap] === scheduled) {
              pendingFutures.remove(bitmap)
            }
          }
          if (!bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
          }
        },
        delayMs,
        TimeUnit.MILLISECONDS
      )
      pendingFutures[bitmap] = scheduled
    }
  }

  fun recycleNow() {
    val toRecycle = ArrayList<Bitmap>()
    synchronized(pendingLock) {
      for ((bitmap, future) in pendingFutures) {
        future.cancel(false)
        toRecycle.add(bitmap)
      }
      pendingFutures.clear()
    }
    for (i in 0 until toRecycle.size) {
      val bitmap = toRecycle[i]
      if (!bitmap.isRecycled) {
        runCatching { bitmap.recycle() }
      }
    }
  }

  @VisibleForTesting
  fun pendingCountForTest(): Int = synchronized(pendingLock) { pendingFutures.size }
}
