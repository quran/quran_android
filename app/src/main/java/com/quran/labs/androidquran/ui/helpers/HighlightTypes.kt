package com.quran.labs.androidquran.ui.helpers

import com.quran.data.model.highlight.HighlightColor
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode.HIDE
import com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.HighlightColors

object HighlightTypes {

  @JvmField
  val SELECTION = HighlightType(1,  R.color.selection_highlight, HIGHLIGHT, isSingle = true)
  @JvmField
  val AUDIO =     HighlightType(2,  R.color.audio_highlight,     HIGHLIGHT, isSingle = true, isTransitionAnimated = true)
  val NOTE =      HighlightType(3,  R.color.note_highlight,      HIGHLIGHT)
  @JvmField
  val BOOKMARK = HighlightType(4, R.color.bookmark_highlight, HIGHLIGHT)

  /**
   * Hifz (memorization) mode: hides ayahs/words via canvas clipping. The id
   * stays clear of the user highlight palette (which starts at
   * FIRST_HIGHLIGHT_ID) and the color is never painted — clipping needs a
   * valid color resource only because the shared paint cache resolves one.
   */
  @JvmField
  val HIFZ = HighlightType(100, android.R.color.transparent, HIDE)

  private const val FIRST_HIGHLIGHT_ID = 5L

  private val HIGHLIGHTS: Map<HighlightColor, HighlightType> =
    HighlightColors.sorted.mapIndexed { index, spec ->
      spec.highlightColor to
          HighlightType(FIRST_HIGHLIGHT_ID + index, spec.colorResourceId, UNDERLAY)
    }.toMap()

  val highlights: Collection<HighlightType> = HIGHLIGHTS.values

  operator fun get(highlightColor: HighlightColor): HighlightType = HIGHLIGHTS.getValue(highlightColor)

  fun getAnimationConfig(type: HighlightType): HighlightAnimationConfig = when(type) {
    AUDIO -> HighlightAnimationConfig.Audio
    else -> HighlightAnimationConfig.None
  }
}
