package com.quran.labs.androidquran.ui.util

import android.view.MotionEvent
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener

interface PageController {
  fun handleTouchEvent(
    event: MotionEvent,
    eventType: AyahSelectedListener.EventType,
    page: Int
  ): Boolean

  fun handleRetryClicked()
  fun onScrollChanged(y: Float)
  fun handleLongPress(suraAyah: SuraAyah)
  fun endAyahMode()
}
