package com.quran.labs.androidquran.common.ui.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight lifecycle hook allowing `:app` (`PagerActivity`, `QuranApplication`) to clear
 * feature-module bitmap caches (e.g. `ImageBitmapUtil` in `:feature:linebyline`) without
 * reflection or reverse module dependencies.
 */
object BitmapPoolTrimRegistry {
  private val cleaners = CopyOnWriteArrayList<() -> Unit>()
  private val restorers = CopyOnWriteArrayList<() -> Unit>()

  fun register(cleaner: () -> Unit) {
    cleaners.addIfAbsent(cleaner)
  }

  fun unregister(cleaner: () -> Unit) {
    cleaners.remove(cleaner)
  }

  fun registerRestorer(restorer: () -> Unit) {
    restorers.addIfAbsent(restorer)
  }

  fun unregisterRestorer(restorer: () -> Unit) {
    restorers.remove(restorer)
  }

  fun clearAll() {
    for (cleaner in cleaners) {
      cleaner()
    }
  }

  fun notifyRestore() {
    for (restorer in restorers) {
      restorer()
    }
  }
}
