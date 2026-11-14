package com.quran.mobile.feature.memorize.presenter

import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode
import com.quran.mobile.feature.memorize.R

/**
 * How memorization shows up on the page. Ids start at 1000 so they never collide with the app's
 * own highlight types (they are keys of a sorted map, ordered by id: on the same word, the lowest
 * id is the one drawn).
 */
internal object MemorizeHighlightTypes {
  /** The word being heard, when a recorded session plays back: over any other mark. */
  val PLAYING = HighlightType(
    1000, R.color.memorize_playing, Mode.UNDERLAY, isSingle = true, nightColorResId = R.color.memorize_playing_night,
  )

  /** Words not recited yet: not drawn at all. */
  val HIDDEN = HighlightType(1001, R.color.memorize_hidden, Mode.HIDE)

  // The marks are backgrounds drawn under the page image, so the words keep their own colors (e.g.
  // on a tajweed-colored mushaf).

  /** Where the user made a mistake: a red background where the (still hidden) word is. */
  val MISTAKE = HighlightType(1002, R.color.memorize_mistake, Mode.UNDERLAY, nightColorResId = R.color.memorize_mistake_night)

  /** Words the app gave as a hint. */
  val HINTED = HighlightType(1003, R.color.memorize_hinted, Mode.UNDERLAY, nightColorResId = R.color.memorize_hinted_night)

  /** Words the user skipped. */
  val MISSED = HighlightType(1004, R.color.memorize_missed, Mode.UNDERLAY, nightColorResId = R.color.memorize_missed_night)

  /**
   * Words read so far, while reciting with the text shown: a light trail where the session follows
   * the reader. Its night colour keeps its own (lower) alpha.
   */
  val RECITED = HighlightType(1005, R.color.memorize_recited, Mode.UNDERLAY, nightColorResId = R.color.memorize_recited_night)

  val ALL = listOf(PLAYING, HIDDEN, MISTAKE, HINTED, MISSED, RECITED)
}
