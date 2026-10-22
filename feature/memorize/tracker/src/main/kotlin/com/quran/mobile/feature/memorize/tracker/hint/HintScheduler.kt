package com.quran.mobile.feature.memorize.tracker.hint

import com.quran.mobile.feature.memorize.tracker.WordId

/**
 * Decides when the prayer corrector gives the user the next word.
 *
 * The behaviour is the one requested for memorization: a wrong word is only *marked* (never
 * revealed); if the user doesn't recover within [HintConfig.delayMs], one hint is given (the
 * caller reveals and plays the next [HintConfig.wordsPerHint] words). If the user still doesn't
 * continue on their own, another hint follows after the same delay, and so on. A pause without a
 * mistake (the user is stuck) is treated the same way once the user has been silent long enough.
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

    /** Reveal and play the next [count] words from the current position. */
    data class GiveHint(val count: Int) : Effect

    /** Too many hints in a row without the user continuing: stop hinting until re-anchored. */
    data object GiveUp : Effect
  }

  private var armed = false
  private var prompting = false
  private var atAyahBoundary = false
  private var lastProgressMs = 0L
  private var speechActive = false
  private var speechEndedMs = 0L
  private var mistake: WordId? = null
  private var mistakeSinceMs = 0L
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
    effects += Effect.MarkMistake(expected)
    return effects
  }

  fun onSpeechActivity(nowMs: Long, active: Boolean) {
    if (speechActive && !active) speechEndedMs = nowMs
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
      if (!config.mistakesWaitForPause) return due
      if (speechActive) return null
      return maxOf(due, speechEndedMs + minSilenceMs())
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
    return clearMistake() + Effect.GiveHint(config.wordsPerHint)
  }

  // Long madd (up to 6 counts) is silence-free but produces no new words, and the recognizer only
  // hears the end of a word up to a second after it was said, so a pause only counts once the user
  // has actually been silent for a while.
  private fun minSilenceMs(): Long = minOf(2_000L, config.delayMs / 2)

  private fun clearMistake(): List<Effect> {
    val pending = mistake ?: return emptyList()
    mistake = null
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
)
