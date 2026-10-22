package com.quran.mobile.feature.memorize.session

import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.data.Passage

/**
 * What a memorization session looks like right now. Recitation is sequential, so every word before
 * [cursor] has been revealed (recited, given as a hint, or missed) and every word from it on is
 * still hidden, unless the user is [peeking].
 */
data class MemorizeState(
  val id: String,
  val passage: Passage,
  val startedAt: Long,
  val status: Status,
  val mode: Mode = Mode.MEMORIZE,
  val cursor: Int = 0,
  val hinted: Set<Int> = emptySet(),
  val missed: Set<Int> = emptySet(),
  /** A hidden word the user got wrong; it is marked but not revealed. */
  val mistake: Int? = null,
  val peeking: Boolean = false,
  /** The recording of this session, if the user chose to record it. */
  val recordingId: String? = null,
  /**
   * Whether it is known where the user started (they may start anywhere around the page on screen):
   * until then every word is hidden; from then on the words before the start are shown.
   */
  val started: Boolean = true,
  /** The word the user started at, once [started] (it may be anywhere on the pages on screen). */
  val firstWord: Int = 0,
) {
  enum class Status { PREPARING, LISTENING, PAUSED, FINISHED }

  enum class Mode {
    /** Reciting from memory with the mushaf page open: words appear as they are recited. */
    MEMORIZE,
    /** Reciting during salah (screen may be off): prayer speech is ignored, hints are spoken. */
    SALAH,
  }

  val isActive: Boolean get() = status == Status.PREPARING || status == Status.LISTENING || status == Status.PAUSED

  // a block body: lint's Kotlin analysis (AGP 9.4) crashes on this as an expression body
  fun isHidden(index: Int): Boolean {
    return !peeking && (!started || index >= cursor)
  }

  val currentAyah: SuraAyah
    get() = passage.ayahOf(cursor.coerceAtMost(passage.words.size - 1))
}
