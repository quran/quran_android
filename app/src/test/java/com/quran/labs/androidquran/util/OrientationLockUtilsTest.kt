package com.quran.labs.androidquran.util

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationLockUtilsTest {
  @Test
  fun `returns unspecified when orientation lock is disabled`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
      OrientationLockUtils.getRequestedOrientation(
        isLockEnabled = false,
        isLandscape = true,
        isSupported = true
      )
    )
  }

  @Test
  fun `returns unspecified when orientation lock is unsupported`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
      OrientationLockUtils.getRequestedOrientation(
        isLockEnabled = true,
        isLandscape = false,
        isSupported = false
      )
    )
  }

  @Test
  fun `requests portrait when portrait lock is enabled`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      OrientationLockUtils.getRequestedOrientation(
        isLockEnabled = true,
        isLandscape = false,
        isSupported = true
      )
    )
  }

  @Test
  fun `requests landscape when landscape lock is enabled`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      OrientationLockUtils.getRequestedOrientation(
        isLockEnabled = true,
        isLandscape = true,
        isSupported = true
      )
    )
  }
}
