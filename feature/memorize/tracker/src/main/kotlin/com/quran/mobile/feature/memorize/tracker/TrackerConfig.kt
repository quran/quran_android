package com.quran.mobile.feature.memorize.tracker

/**
 * Tuning of [RecitationTracker]. The defaults come from the reference implementation
 * (tools/memorize tracker) and the benchmark on real recordings.
 */
data class TrackerConfig(
  /** How far back (in words) a repetition may start. */
  val backWords: Int = 8,
  /** Expected words considered past the cursor for normal tracking. */
  val aheadWords: Int = 6,
  /** Cost per heard phoneme skipped before the alignment starts (false starts, noise). */
  val garbageCost: Double = 0.55,
  val consonantSub: Double = 1.0,
  val confusableSub: Double = 0.6,
  val vowelSub: Double = 0.5,
  val vowelIndel: Double = 0.5,
  val indelCost: Double = 1.0,
  /** Insert/delete cost within [edge] phonemes of a word boundary (wasl/waqf differences). */
  val edgeIndelCost: Double = 0.35,
  val edge: Int = 2,
  /** Extra phonemes heard past the expected word before a mistake is declared. */
  val mistakeMargin: Int = 3,
  val maxPending: Int = 80,
  /** Confidently recited words needed to catch up past an unmatched word. */
  val resyncRun: Int = 2,
  /** Fraction of a word that must have been heard to accept it from its context. */
  val contextMinHeard: Double = 0.4,
  /** Cost per phoneme allowed when accepting a word from its context. */
  val contextCost: Double = 0.7,
  /** How far ahead (in words) to look when the user is lost. */
  val farWords: Int = 40,
  val recoverTail: Int = 18,
  val recoverMinTail: Int = 12,
  val recoverCost: Double = 0.2,
  /**
   * The most that sounds heard before a phrase may cost (the rest of a drawn-out word, or an earlier
   * phrase that went unrecognized), so a takbir is still recognized after them.
   */
  val phraseGarbageCap: Double = 1.0,
) {
  fun allowance(length: Int): Double = when {
    length <= 3 -> 0.5
    length <= 7 -> 1.0
    else -> 0.18 * length
  }
}
