package com.quran.mobile.feature.memorize.tracker

/** A recognized phoneme (a single normalized symbol) and when it was heard. */
data class TimedPhoneme(val symbol: Char, val timeMs: Long)

/** Events produced by [RecitationTracker]. Indexes refer to the passage's word list. */
sealed interface TrackerEvent {
  val index: Int
  val timeMs: Long

  /** The word was recited (reveal it). */
  data class WordRecited(
    override val index: Int,
    override val timeMs: Long,
    val cost: Double,
    /** Accepted from its context: the words around it were clearly recited. */
    val fromContext: Boolean = false,
    /** Found again after the user was lost. */
    val recovered: Boolean = false,
  ) : TrackerEvent

  /** The user moved on without saying this word. */
  data class WordMissed(override val index: Int, override val timeMs: Long) : TrackerEvent

  /** The user went back and repeated words [index]..[lastIndex] (normal while memorizing). */
  data class Repetition(override val index: Int, val lastIndex: Int, override val timeMs: Long) : TrackerEvent

  /** The user said something other than the word at [index]. */
  data class Mistake(override val index: Int, override val timeMs: Long, val kind: Kind) : TrackerEvent {
    enum class Kind { SUBSTITUTION, SKIP }
  }

  /** A phrase that isn't part of the passage (isti'adha, basmala, takbir...) was said. */
  data class NonTarget(override val index: Int, override val timeMs: Long, val kind: String) : TrackerEvent
}
