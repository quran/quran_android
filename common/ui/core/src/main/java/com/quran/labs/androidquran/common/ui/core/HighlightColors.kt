package com.quran.labs.androidquran.common.ui.core

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.quran.data.model.highlight.HighlightColor
import com.quran.mobile.common.ui.core.R

@Immutable
data class HighlightColorSpec(
  val highlightColor: HighlightColor,
  val color: Color,
  @StringRes val nameResourceId: Int
)

object HighlightColors {
  private val yellow = HighlightColorSpec(
    highlightColor = HighlightColor.YELLOW,
    color = Color(0xFFFDEC63),
    nameResourceId = R.string.highlight_color_yellow
  )

  private val green = HighlightColorSpec(
    highlightColor = HighlightColor.GREEN,
    color = Color(0xFFC1EC71),
    nameResourceId = R.string.highlight_color_green
  )

  private val blue = HighlightColorSpec(
    highlightColor = HighlightColor.BLUE,
    color = Color(0xFFADD7FE),
    nameResourceId = R.string.highlight_color_blue
  )

  private val red = HighlightColorSpec(
    highlightColor = HighlightColor.RED,
    color = Color(0xFFFEB0CA),
    nameResourceId = R.string.highlight_color_red
  )

  private val purple = HighlightColorSpec(
    highlightColor = HighlightColor.PURPLE,
    color = Color(0xFFD8B1FE),
    nameResourceId = R.string.highlight_color_purple
  )

  val sorted: List<HighlightColorSpec> = listOf(yellow, green, blue, red, purple)
  val contentColor: Color = Color.Black.copy(alpha = 0.72f)

  operator fun get(highlightColor: HighlightColor): HighlightColorSpec {
    return when (highlightColor) {
      HighlightColor.YELLOW -> yellow
      HighlightColor.GREEN -> green
      HighlightColor.BLUE -> blue
      HighlightColor.RED -> red
      HighlightColor.PURPLE -> purple
    }
  }
}
