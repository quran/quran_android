package com.quran.mobile.feature.sync

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSyncTest {
  private val owner = object : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
  }
  private var syncCount = 0
  private val foregroundSync = ForegroundSync { syncCount++ }

  @Test fun `launch does not trigger foreground sync`() {
    foregroundSync.onStart(owner)

    assertEquals(0, syncCount)
  }

  @Test fun `return from background triggers sync`() {
    foregroundSync.onStart(owner)
    foregroundSync.onStop(owner)
    foregroundSync.onStart(owner)

    assertEquals(1, syncCount)
  }

  @Test fun `each return triggers sync only once`() {
    foregroundSync.onStop(owner)
    foregroundSync.onStart(owner)
    foregroundSync.onStart(owner)
    assertEquals(1, syncCount)

    foregroundSync.onStop(owner)
    foregroundSync.onStart(owner)
    assertEquals(2, syncCount)
  }
}
