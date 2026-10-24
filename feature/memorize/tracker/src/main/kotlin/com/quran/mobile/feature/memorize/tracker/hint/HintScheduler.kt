package com.quran.mobile.feature.memorize.tracker.hint

import com.quran.mobile.feature.memorize.tracker.WordId

/**
 * Decides when the prayer corrector gives the user the next word.
 *
 * The behaviour is the one requested for memorization: a wrong word is only *marked* (never
 * revealed); if the user doesn't recover within [HintConfig.delayMs], one hint is given (the
 * caller reveals and plays the next [HintConfig.wordsPerHint] words). A user who tries the word
 * again and gets it wrong again is given it at their next pause, without waiting for the delay. If
 * the user still doesn't continue on their own, another hint follows after the same delay, and so
 * on; after [HintConfig.wordHintsBeforeLonger] of them each hint gives the rest of the ayah or of
 * the line ([Extent.AYAH_OR_LINE]). A pause without a mistake (the user is stuck) is treated the
 * same way once the user has been silent long enough.
 *
 * The scheduler is a pure state machine: every input carries the current time and every output is
 * returned as a list of [Effect]s, so it can be driven by a real clock on the device and by a fake
 * one in tests. While a hint is being played, time doesn't count towards the next hint.
 */
class HintScheduler(private val config: HintConfig) {

  sealed interface Effect {
    /** Mark the hidden word the user should have said, without revealing it. */
    data class MarkMistake(val word: WordId) : Effect

    /** Remove a mistake mark (the user recovered, or a hint revealed the word). */
    data class ClearMistake(val word: WordId) : Effect

    /** Reveal and play the next [count] words (or more, see [extent]) from the current position. */
    data class GiveHint(val count: Int, val extent: Extent = Extent.WORDS) : Effect

    /** Too many hints in a row without the user continuing: stop hinting until re-anchored. */
    data object GiveUp : Effect
  }

  enum class Extent {
    /** The configured number of words. */
    WORDS,
    /** The rest of the ayah if it is short, else the rest of the line on the page. */
    AYAH_OR_LINE,
  }

  private var armed = false
  private var prompting = false
  private var atAyahBoundary = false
  private var lastProgressMs = 0L
  private var speechActive = false
  private var speechEndedMs = 0L
  private var mistake: WordId? = null
  private var mistakeSinceMs = 0L
  // how many times the user has tried the word of the pending mistake and stopped
  private var attempts = 0
  private var consecutiveHints = 0

  val isArmed: Boolean get() = armed

  /**
   * Starts (or restarts) hinting. [atAyahBoundary] is true when the user is between ayat: the next
   * word starts an ayah, or ends one (see [onWordRecited]).
   */
  fun arm(nowMs: Long, atAyahBoundary: Boolean): List<Effect> {
    val effects = clearMistake()
    armed = true
    prompting = false
    consecutiveHints = 0
    lastProgressMs = nowMs
    speechEndedMs = nowMs
    this.atAyahBoundary = atAyahBoundary
    return effects
  }

  /** Stops hinting, e.g. between rak'ahs or when the session pauses. */
  fun disarm(): List<Effect> {
    armed = false
    prompting = false
    return clearMistake()
  }

  /**
   * The user recited a new word correctly. [atAyahBoundary] is true when the next word starts an
   * ayah or is its last word: a reciter who stops there has usually finished the ayah (its last
   * word may still be on its way through the recognizer).
   */
  fun onWordRecited(nowMs: Long, atAyahBoundary: Boolean): List<Effect> {
    lastProgressMs = nowMs
    consecutiveHints = 0
    this.atAyahBoundary = atAyahBoundary
    return clearMistake()
  }

  /** The user repeated words they already said (normal while memorizing): they're still going. */
  fun onRepetition(nowMs: Long) {
    lastProgressMs = nowMs
  }

  /** The user said something other than [expected]. */
  fun onMistake(nowMs: Long, expected: WordId): List<Effect> {
    if (!armed || !config.onMistakes) return emptyList()
    if (mistake == expected) return emptyList()
    val effects = clearMistake().toMutableList()
    mistake = expected
    mistakeSinceMs = nowMs
    // recognized only after the user stopped: that attempt is over already
    attempts = if (speechActive) 0 else 1
    effects += Effect.MarkMistake(expected)
    return effects
  }

  fun onSpeechActivity(nowMs: Long, active: Boolean) {
    if (speechActive && !active) {
      speechEndedMs = nowMs
      if (mistake != null) attempts++
    }
    speechActive = active
  }

  /**
   * A hint started playing; the delay is suspended until [onHintFinished]. The hint gives the word a
   * pending mistake was about, so the mistake is settled (its mark was cleared with the reveal).
   */
  fun onHintStarted() {
    prompting = true
    mistake = null
  }

  /** The hint finished playing (or there was no audio to play). The delay restarts now. */
  fun onHintFinished(nowMs: Long) {
    prompting = false
    lastProgressMs = nowMs
    speechEndedMs = maxOf(speechEndedMs, nowMs)
  }

  /** The time at which [onTick] will next do something, or null if nothing is pending. */
  fun nextDeadlineMs(): Long? {
    if (!armed || prompting) return null
    if (mistake != null) {
      val due = mistakeSinceMs + config.delayMs
      val afterDelay = when {
        !config.mistakesWaitForPause -> due
        speechActive -> null
        else -> maxOf(due, speechEndedMs + minSilenceMs())
      }
      // wrong again: give the word at this pause, once the recognizer has had time to catch up
      val repeated = if (attempts >= config.mistakeAttemptsBeforeHint && !speechActive) {
        speechEndedMs + minSilenceMs()
      } else {
        null
      }
      return listOfNotNull(afterDelay, repeated).minOrNull()
    }
    if (!config.onPauses || speechActive) return null
    val delay = if (atAyahBoundary) config.ayahBoundaryDelayMs ?: return null else config.delayMs
    return maxOf(lastProgressMs + delay, speechEndedMs + minSilenceMs())
  }

  fun onTick(nowMs: Long): List<Effect> {
    val deadline = nextDeadlineMs() ?: return emptyList()
    if (nowMs < deadline) return emptyList()

    if (consecutiveHints >= config.maxConsecutiveHints) {
      armed = false
      return clearMistake() + Effect.GiveUp
    }
    consecutiveHints++
    prompting = true
    val extent = if (consecutiveHints > config.wordHintsBeforeLonger) Extent.AYAH_OR_LINE else Extent.WORDS
    return clearMistake() + Effect.GiveHint(config.wordsPerHint, extent)
  }

  // Long madd (up to 6 counts) is silence-free but produces no new words, and the recognizer only
  // hears the end of a word up to a second after it was said, so a pause only counts once the user
  // has actually been silent for a while.
  private fun minSilenceMs(): Long = minOf(2_000L, config.delayMs / 2)

  private fun clearMistake(): List<Effect> {
    val pending = mistake ?: return emptyList()
    mistake = null
    attempts = 0
    return listOf(Effect.ClearMistake(pending))
  }
}

data class HintConfig(
  val delayMs: Long = 4_000L,
  val wordsPerHint: Int = 1,
  val onMistakes: Boolean = true,
  val onPauses: Boolean = true,
  /**
   * How long a pause between ayat (before an ayah's first word or on its last) must last to count
   * as being stuck, or null if it never does. In salah such a pause is often ruku, which the takbir
   * then confirms.
   */
  val ayahBoundaryDelayMs: Long? = delayMs,
  /**
   * Whether a mistake is only hinted once the reciter pauses. In salah the hint is spoken: a reciter
   * who carries on fluently has more often been misheard than mistaken, and is corrected at their
   * next pause otherwise.
   */
  val mistakesWaitForPause: Boolean = false,
  val maxConsecutiveHints: Int = 10,
  /** Wrong attempts at the same word after which it is given at the user's next pause. */
  val mistakeAttemptsBeforeHint: Int = 2,
  /** Hints of [wordsPerHint] words in a row after which hints give the rest of the ayah or line. */
  val wordHintsBeforeLonger: Int = 3,
)
