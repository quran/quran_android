package com.quran.labs.androidquran.ui.helpers

import com.quran.data.model.highlight.HighlightColor
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.HighlightColors

object HighlightTypes {

  // ids order the highlights against each other - lower ids are drawn first
  private val HIGHLIGHTS: Map<HighlightColor, HighlightType> =
    HighlightColors.sorted.mapIndexed { index, spec ->
      spec.highlightColor to
          HighlightType(index.toLong(), spec.colorResourceId, UNDERLAY)
    }.toMap()

  @JvmField
  val SELECTION = HighlightType(10, R.color.selection_highlight, HIGHLIGHT, isSingle = true)
  @JvmField
  val AUDIO =     HighlightType(11, R.color.audio_highlight,     HIGHLIGHT, isSingle = true, isTransitionAnimated = true)
  val NOTE =      HighlightType(12, R.color.note_highlight,      HIGHLIGHT)
  @JvmField
  val BOOKMARK =  HighlightType(13, R.color.bookmark_highlight,  HIGHLIGHT)

  val highlights: Collection<HighlightType> = HIGHLIGHTS.values

  operator fun get(highlightColor: HighlightColor): HighlightType = HIGHLIGHTS.getValue(highlightColor)

  fun getAnimationConfig(type: HighlightType): HighlightAnimationConfig = when(type) {
    AUDIO -> HighlightAnimationConfig.Audio
    else -> HighlightAnimationConfig.None
  }
}
