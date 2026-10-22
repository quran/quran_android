package com.quran.mobile.feature.memorize.tracker.salah

import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerConfig
import com.quran.mobile.feature.memorize.tracker.TrackerEvent

/**
 * Follows a passage recited during salah. Besides the passage the person says Al-Fatiha in every
 * rak'ah and the phrases of the prayer (takbir, tasmi', tahmid, tasbeeh, tasleem...), and pauses
 * for ruku and sujud. None of that may be treated as a mistake, and the passage continues in the
 * next rak'ah from where it stopped.
 *
 * Two trackers listen to the same phonemes: one for the passage (with the prayer phrases as known
 * non-target speech) and one for Al-Fatiha. While Al-Fatiha is being recited, whatever the passage
 * tracker made of it is discarded.
 */
class SalahFlow(
  passageWords: List<String>,
  private val fatihaWords: List<String>,
  phrases: List<Pair<String, String>>,
  private val config: TrackerConfig = TrackerConfig(),
  /** Where the passage may start (see [RecitationTracker]): e.g. the pages around the one on screen. */
  startWindow: Int = 0,
  /** Where the user is expected to start (e.g. the page on screen), within [startWindow]. */
  startIndex: Int = 0,
) {
  enum class Phase {
    /** Before the passage starts in this rak'ah (opening takbir, istiftah, Al-Fatiha...). */
    AWAITING,
    /** Reciting Al-Fatiha. */
    FATIHA,
    /** Reciting the passage: the only phase in which the corrector may help. */
    PASSAGE,
    /** After the passage in this rak'ah: ruku, sujud, tashahhud... until the next Al-Fatiha. */
    BETWEEN_RAKAHS,
  }

  val passage = RecitationTracker(passageWords, config, startIndex, phrases, startWindow)
  private var fatiha = newFatihaTracker()
  // this rak'ah's Al-Fatiha is over: what follows should be the passage
  private var afterFatiha = false
  // phonemes heard since the last word of Al-Fatiha: while it is recited, the passage's start isn't
  // looked for (in a few pages of Quran, some of its words match nearly anything)
  private var sinceFatiha = Int.MAX_VALUE

  var phase: Phase = Phase.AWAITING
    private set

  /** Feeds the phonemes to both trackers; returns the passage's events that matter. */
  fun feed(phonemes: List<TimedPhoneme>): List<TrackerEvent> {
    val fatihaEvents = fatiha.feed(phonemes)
    val inFatiha = fatihaEvents.any { it is TrackerEvent.WordRecited }
    sinceFatiha = if (inFatiha) 0 else (sinceFatiha + phonemes.size).coerceAtMost(Int.MAX_VALUE / 2)
    if (!passage.anchored && sinceFatiha < FATIHA_QUIET_PHONEMES) {
      phase = Phase.FATIHA
      passage.discardPending()
      if (fatiha.isDone) {
        fatiha = newFatihaTracker()
        phase = Phase.AWAITING
        afterFatiha = true
      }
      return emptyList()
    }
    val passageEvents = passage.feed(phonemes)

    val passageProgress = passageEvents.any { it is TrackerEvent.WordRecited }

    if (inFatiha && !passageProgress) {
      phase = Phase.FATIHA
      passage.discardPending()
      if (fatiha.isDone) {
        // ready for the next rak'ah; the passage continues where it stopped
        fatiha = newFatihaTracker()
        phase = Phase.AWAITING
        afterFatiha = true
      }
      return emptyList()
    }

    if (passageProgress) {
      phase = Phase.PASSAGE
      afterFatiha = false
      // the user moved on to the passage: Al-Fatiha starts from its beginning next time
      if (fatiha.cursor > 0) fatiha = newFatihaTracker()
    } else if (phase == Phase.PASSAGE && passageEvents.any { it.endsPassage() }) {
      phase = Phase.BETWEEN_RAKAHS
    }
    if (passageEvents.any { it.endsPassage() }) afterFatiha = false

    if (phase != Phase.PASSAGE) {
      // outside the passage nothing is a mistake, except a passage recited after Al-Fatiha that
      // isn't where the user said it would be
      return passageEvents.filter {
        it !is TrackerEvent.Mistake || (afterFatiha && it.kind == TrackerEvent.Mistake.Kind.ELSEWHERE)
      }
    }
    return passageEvents
  }

  /** More of the passage (the next sura); the session doesn't end with the current one. */
  fun extendPassage(more: List<String>) = passage.extend(more)

  /** The user stopped making sound (e.g. after the takbir, for ruku). */
  fun onPause(): List<TrackerEvent> {
    val events = passage.onPause()
    if (phase == Phase.PASSAGE && events.any { it.endsPassage() }) {
      phase = Phase.BETWEEN_RAKAHS
    }
    if (events.any { it.endsPassage() }) afterFatiha = false
    return events
  }

  // Any phrase of ruku or sujud means the passage of this rak'ah is over, even if the takbir before
  // it wasn't recognized.
  private fun TrackerEvent.endsPassage() = this is TrackerEvent.NonTarget && kind in ENDS_PASSAGE

  /** Whether the corrector may help now. */
  val isReadingPassage: Boolean get() = phase == Phase.PASSAGE

  private fun newFatihaTracker() = RecitationTracker(fatihaWords, config)

  companion object {
    const val TAKBIR = "takbir"
    /** Phonemes after Al-Fatiha's last recognized word before the passage's start is looked for. */
    private const val FATIHA_QUIET_PHONEMES = 12

    /** Phrases said only once the passage of a rak'ah is over. */
    val ENDS_PASSAGE = setOf(TAKBIR, "tasmi", "tahmid", "ruku", "sujud", "tasleem")

    /** The phrase kinds (from the lexicon) said during salah that aren't part of the passage. */
    val PRAYER_PHRASES = setOf(
      "takbir", "tasmi", "tahmid", "ruku", "sujud", "ameen", "isti'adha", "basmala", "istiftah", "tasleem",
    )
  }
}
