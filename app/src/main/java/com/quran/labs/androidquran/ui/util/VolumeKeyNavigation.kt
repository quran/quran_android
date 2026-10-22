package com.quran.labs.androidquran.ui.util

import android.view.KeyEvent

object VolumeKeyNavigation {

  /**
   * Returns how many positions the pager should move when [keyCode] is pressed with volume key
   * navigation on, or 0 if [keyCode] is not a volume key.
   *
   * Pager positions run opposite to page numbers, so +1 is the previous page. By default volume
   * down goes to the next page and volume up to the previous one; [swapped] reverses this (useful
   * for single button page turners that send volume up).
   */
  fun pagerOffset(keyCode: Int, swapped: Boolean): Int {
    val offset = when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_DOWN -> -1
      KeyEvent.KEYCODE_VOLUME_UP -> 1
      else -> return 0
    }
    return if (swapped) -offset else offset
  }
}
