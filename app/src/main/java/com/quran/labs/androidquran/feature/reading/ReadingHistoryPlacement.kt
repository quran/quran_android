package com.quran.labs.androidquran.feature.reading

object ReadingHistoryPlacement {
  fun bottomMargin(
    audioBarHeight: Int,
    navigationInset: Int,
    fallbackAudioHeight: Int,
    audioBarVisible: Boolean
  ): Int {
    return when {
      audioBarVisible && audioBarHeight > 0 -> audioBarHeight
      audioBarVisible -> navigationInset + fallbackAudioHeight
      else -> navigationInset
    }
  }
}
