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

  val passage = RecitationTracker(passageWords, config, phrases = phrases)
  private var fatiha = newFatihaTracker()

  var phase: Phase = Phase.AWAITING
    private set

  /** Feeds the phonemes to both trackers; returns the passage's events that matter. */
  fun feed(phonemes: List<TimedPhoneme>): List<TrackerEvent> {
    val fatihaEvents = fatiha.feed(phonemes)
    val passageEvents = passage.feed(phonemes)

    val inFatiha = fatihaEvents.any { it is TrackerEvent.WordRecited }
    val passageProgress = passageEvents.any { it is TrackerEvent.WordRecited }

    if (inFatiha && !passageProgress) {
      phase = Phase.FATIHA
      passage.discardPending()
      if (fatiha.isDone) {
        // ready for the next rak'ah; the passage continues where it stopped
        fatiha = newFatihaTracker()
        phase = Phase.AWAITING
      }
      return emptyList()
    }

    if (passageProgress) {
      phase = Phase.PASSAGE
      // the user moved on to the passage: Al-Fatiha starts from its beginning next time
      if (fatiha.cursor > 0) fatiha = newFatihaTracker()
    } else if (phase == Phase.PASSAGE && passageEvents.any { it.endsPassage() }) {
      phase = Phase.BETWEEN_RAKAHS
    }

    if (phase != Phase.PASSAGE) {
      // outside the passage nothing is a mistake
      return passageEvents.filter { it !is TrackerEvent.Mistake }
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

    /** Phrases said only once the passage of a rak'ah is over. */
    val ENDS_PASSAGE = setOf(TAKBIR, "tasmi", "tahmid", "ruku", "sujud", "tasleem")

    /** The phrase kinds (from the lexicon) said during salah that aren't part of the passage. */
    val PRAYER_PHRASES = setOf(
      "takbir", "tasmi", "tahmid", "ruku", "sujud", "ameen", "isti'adha", "basmala", "istiftah", "tasleem",
    )
  }
}
