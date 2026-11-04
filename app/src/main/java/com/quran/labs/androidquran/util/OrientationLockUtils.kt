package com.quran.labs.androidquran.util

import android.content.pm.ActivityInfo
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

  @JvmStatic
  fun getRequestedOrientation(
    isLockEnabled: Boolean,
    isLandscape: Boolean,
    isSupported: Boolean
  ): Int {
    if (!isLockEnabled || !isSupported) {
      return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    return if (isLandscape) {
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
  }
}
