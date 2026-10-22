package com.quran.mobile.feature.memorize.presenter

import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode
import com.quran.mobile.feature.memorize.R

/**
 * How memorization shows up on the page. Ids start at 1001 so they never collide with the app's
 * own highlight types (they are keys of a sorted map, ordered by id).
 */
internal object MemorizeHighlightTypes {
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

  val ALL = listOf(HIDDEN, MISTAKE, HINTED, MISSED)
}
