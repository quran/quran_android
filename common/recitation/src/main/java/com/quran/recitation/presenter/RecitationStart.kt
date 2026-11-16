package com.quran.recitation.presenter

/** How the user asked to start a session from the reading screen. */
enum class RecitationStart {
  /** Right away, the usual way (e.g. the toolbar's "Memorize"). */
  DEFAULT,

  /** Let the user choose how to recite (a microphone was tapped). */
  CHOOSE,

  /** Let the user choose among every way there is (a microphone was long-pressed). */
  CHOOSE_ALL,
}
