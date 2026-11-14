package com.quran.mobile.feature.memorize.prompt

import com.quran.mobile.feature.memorize.tracker.WordId

/** Speaks the words given as a hint. */
interface HintVoice {
  /** The audio of some words, ready to be played. */
  interface Clip

  /**
   * Finds the audio of [words] (consecutive words of one sura) in the voice of [qariId], or null if
   * there is none to play. Reads files: call it off the main thread.
   */
  fun prepare(qariId: Int, words: List<WordId>): Clip?

  /**
   * Called on the main thread. [onFinished] is called on the main thread when the clip has been
   * played, and never if [stop] or another [play] cuts it short.
   */
  fun play(clip: Clip, onStarted: () -> Unit, onFinished: () -> Unit)

  /** Stops speaking, without calling onFinished. */
  fun stop()
}
