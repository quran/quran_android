package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.collections.immutable.ImmutableList

private const val InsetRatio = 1f / 358f
private const val BottomOffsetRatio = 7f / 358f
private const val ThicknessRatio = 4.5f / 358f

@Composable
fun ReadingBookmarkUnderline(
  lineId: Int,
  left: Float,
  right: Float,
  lineRatio: Float,
  colors: ImmutableList<Color>
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val width = this.size.width
    val lineHeight = width * lineRatio
    val lastLineIndex = 14f
    val lineHeightWithoutOverlap = (this.size.height - lineHeight) / lastLineIndex
    val yStart = (lineHeight - lineHeightWithoutOverlap) / 2
    val lineBottom = yStart + lineHeightWithoutOverlap * (lineId + 1)

    val inset = InsetRatio * width
    val thickness = ThicknessRatio * width
    val x = left * width + inset
    val y = lineBottom - BottomOffsetRatio * width
    val underlineWidth = (right - left) * width - 2 * inset
    val cornerRadius = CornerRadius(thickness / 2)

    if (colors.size == 1) {
      drawRoundRect(colors.first(), Offset(x, y), Size(underlineWidth, thickness), cornerRadius)
    } else {
      val pill = Path().apply {
        addRoundRect(RoundRect(x, y, x + underlineWidth, y + thickness, cornerRadius))
      }

      // first slot starts at the right, where the line begins
      val segmentWidth = underlineWidth / colors.size
      clipPath(pill) {
        colors.forEachIndexed { index, color ->
          val segmentStart = x + underlineWidth - segmentWidth * (index + 1)
          drawRect(color, Offset(segmentStart, y), Size(segmentWidth, thickness))
        }
      }
    }
  }
}
