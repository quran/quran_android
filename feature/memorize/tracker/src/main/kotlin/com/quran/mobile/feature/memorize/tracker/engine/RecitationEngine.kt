package com.quran.mobile.feature.memorize.tracker.engine

/**
 * An on-device speech recognizer that turns recitation audio into phoneme tokens of the
 * quran-transcript phonetic script. Implementations must not send audio anywhere.
 */
interface RecitationEngine : AutoCloseable {
  /** Starts recognizing a new, independent audio stream. */
  fun newStream(): RecitationStream
}

interface RecitationStream : AutoCloseable {
  /**
   * Feeds 16 kHz mono samples in [-1, 1] and returns the tokens recognized since the previous
   * call. Tokens are final once returned. [speech] is whether the caller's voice activity detector
   * heard sound in them: the stream may start over in real silence, but must never cut a word.
   */
  fun accept(samples: FloatArray, count: Int = samples.size, speech: Boolean = true): List<RecognizedToken>

  /** Signals the end of the audio and returns any remaining tokens. */
  fun finish(): List<RecognizedToken>
}

/**
 * @param text a unit of the phonetic script (may be several characters, e.g. a letter and its vowel)
 * @param timeMs when it was heard, relative to the start of the stream
 * @param probability the model's confidence in it, if available
 */
data class RecognizedToken(val text: String, val timeMs: Long, val probability: Float? = null)
