package com.quran.labs.androidquran.ui.helpers

class TransitionAyahHighlight(
  val source: AyahHighlight,
  val destination: AyahHighlight
) : AyahHighlight(key = "${source.key}->${destination.key}", isTransition = true)
