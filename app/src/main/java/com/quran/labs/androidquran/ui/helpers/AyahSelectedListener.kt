package com.quran.labs.androidquran.ui.helpers

import com.quran.data.model.SuraAyah

interface AyahSelectedListener {

  enum class EventType { SINGLE_TAP, LONG_PRESS, DOUBLE_TAP }

  /** Return true to receive the ayah info along with the
   * click event, false to receive just the event type  */
  fun isListeningForAyahSelection(eventType: EventType): Boolean

  /** Click event with ayah info and highlighter passed  */
  fun onAyahSelected(
    eventType: EventType, suraAyah: SuraAyah, tracker: AyahTracker
  ): Boolean

  /** General click event without ayah info  */
  fun onClick(eventType: EventType): Boolean

  /** end ayah mode  */
  fun endAyahMode()

  /** set menu bar position  */
  fun requestMenuPositionUpdate(tracker: AyahTracker)
}
