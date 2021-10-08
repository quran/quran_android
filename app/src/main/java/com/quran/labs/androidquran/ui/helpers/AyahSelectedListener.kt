package com.quran.labs.androidquran.ui.helpers

interface AyahSelectedListener {

  enum class EventType { SINGLE_TAP, LONG_PRESS, DOUBLE_TAP }

  /** set menu bar position  */
  fun requestMenuPositionUpdate(tracker: AyahTracker)
}
