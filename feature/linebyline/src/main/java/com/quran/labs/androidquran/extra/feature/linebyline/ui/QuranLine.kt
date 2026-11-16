package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import kotlin.math.floor

@Composable
fun QuranLine(
  image: ImageBitmap,
  lineId: Int,
  lineRatio: Float,
  colorFilter: ColorFilter,
  drawDivider: Boolean,
  lineColor: Color,
  // parts of the line not to draw (left and right as fractions of its width)
  hidden: List<AyahHighlight> = emptyList()
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val totalWidth = this.size.width
    val totalHeight = this.size.height

    val lineHeight = totalWidth * lineRatio
    val lastLineIndex = 14f
    val y = floor((totalHeight - lineHeight) / lastLineIndex * lineId).toInt()
    val drawLineImage = {
      drawImage(
        image = image,
        dstOffset = IntOffset(0, y),
        dstSize = IntSize(totalWidth.toInt(), lineHeight.toInt()),
        colorFilter = colorFilter
      )
    }
    if (hidden.isEmpty()) {
      drawLineImage()
    } else {
      // the whole height of this line's image: lines overlap, but each is drawn on its own
      val hiddenParts = Path().apply {
        hidden.forEach { addRect(Rect(it.left * totalWidth, y.toFloat(), it.right * totalWidth, y + lineHeight)) }
      }
      clipPath(hiddenParts, ClipOp.Difference) { drawLineImage() }
    }

    if (drawDivider) {
      drawLine(lineColor, start = Offset(0f, y.toFloat()), end = Offset(totalWidth, y.toFloat()))
    }
  }
}
