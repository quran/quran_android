package com.quran.labs.androidquran.util

import android.content.res.Configuration
import android.os.Build

object OrientationLockUtils {
  private const val ANDROID_16_API_LEVEL = 36
  private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

  @JvmStatic
  fun isOrientationLockSupported(configuration: Configuration): Boolean {
    // Android 16+ ignores app-requested orientation locks on large screens.
    return Build.VERSION.SDK_INT < ANDROID_16_API_LEVEL ||
      configuration.smallestScreenWidthDp < LARGE_SCREEN_SMALLEST_WIDTH_DP
  }
}
