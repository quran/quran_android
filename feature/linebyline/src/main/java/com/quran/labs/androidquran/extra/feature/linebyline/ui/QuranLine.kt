package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

@Composable
fun QuranLine(
  image: ImageBitmap,
  lineId: Int,
  lineRatio: Float,
  colorFilter: ColorFilter,
  drawDivider: Boolean,
  lineColor: Color
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val totalWidth = this.size.width
    val totalHeight = this.size.height

    val lineHeight = totalWidth * lineRatio
    val lastLineIndex = 14f
    val y = floor((totalHeight - lineHeight) / lastLineIndex * lineId).toInt()
    drawImage(
      image = image,
      dstOffset = IntOffset(0, y),
      dstSize = IntSize(totalWidth.toInt(), lineHeight.toInt()),
      colorFilter = colorFilter
    )

    if (drawDivider) {
      drawLine(lineColor, start = Offset(0f, y.toFloat()), end = Offset(totalWidth, y.toFloat()))
    }
  }
}
