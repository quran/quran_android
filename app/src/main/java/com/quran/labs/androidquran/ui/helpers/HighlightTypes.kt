package com.quran.labs.androidquran.ui.helpers

import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor
import com.quran.data.model.highlight.HighlightType
import com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY
import com.quran.data.model.highlight.HighlightType.Mode.UNDERLINE
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.mobile.common.ui.core.R as UiCoreR

object HighlightTypes {

  @JvmField
  val SELECTION = HighlightType(1,  R.color.selection_highlight, HIGHLIGHT, isSingle = true)
  @JvmField
  val AUDIO =     HighlightType(2,  R.color.audio_highlight,     HIGHLIGHT, isSingle = true, isTransitionAnimated = true)
  val NOTE =      HighlightType(3,  R.color.note_highlight,      HIGHLIGHT)
  @JvmField
  val BOOKMARK =  HighlightType(4,  R.color.bookmark_highlight,  HIGHLIGHT)

  private const val FIRST_HIGHLIGHT_ID = 5L

  private val HIGHLIGHTS: Map<HighlightColor, HighlightType> =
    HighlightColors.sorted.mapIndexed { index, spec ->
      spec.highlightColor to
          HighlightType(FIRST_HIGHLIGHT_ID + index, spec.colorResourceId, UNDERLAY)
    }.toMap()

  val highlights: Collection<HighlightType> = HIGHLIGHTS.values

  operator fun get(highlightColor: HighlightColor): HighlightType = HIGHLIGHTS.getValue(highlightColor)

  private val FIRST_READING_BOOKMARK_ID = FIRST_HIGHLIGHT_ID + HIGHLIGHTS.size

  private val READING_BOOKMARKS: Map<ReadingBookmarkType, HighlightType> = mapOf(
    ReadingBookmarkType.GREEN to HighlightType(
      FIRST_READING_BOOKMARK_ID,
      UiCoreR.color.reading_bookmark_page_green,
      UNDERLINE,
      nightColorResId = UiCoreR.color.reading_bookmark_page_green_night
    ),
    ReadingBookmarkType.PURPLE to HighlightType(
      FIRST_READING_BOOKMARK_ID + 1,
      UiCoreR.color.reading_bookmark_page_purple,
      UNDERLINE,
      nightColorResId = UiCoreR.color.reading_bookmark_page_purple_night
    ),
    ReadingBookmarkType.BLUE to HighlightType(
      FIRST_READING_BOOKMARK_ID + 2,
      UiCoreR.color.reading_bookmark_page_blue,
      UNDERLINE,
      nightColorResId = UiCoreR.color.reading_bookmark_page_blue_night
    )
  )

  val readingBookmarks: Collection<HighlightType> = READING_BOOKMARKS.values

  operator fun get(slot: ReadingBookmarkType): HighlightType = READING_BOOKMARKS.getValue(slot)

  fun getAnimationConfig(type: HighlightType): HighlightAnimationConfig = when(type) {
    AUDIO -> HighlightAnimationConfig.Audio
    else -> HighlightAnimationConfig.None
  }
}
