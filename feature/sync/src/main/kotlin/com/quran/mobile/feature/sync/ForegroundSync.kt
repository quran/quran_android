package com.quran.mobile.feature.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class ForegroundSync(
  private val syncOnForeground: () -> Unit
) : DefaultLifecycleObserver {
  private var isInBackground = false

  override fun onStop(owner: LifecycleOwner) {
    isInBackground = true
  }

  override fun onStart(owner: LifecycleOwner) {
    if (isInBackground) {
      isInBackground = false
      syncOnForeground()
    }
  }
}
