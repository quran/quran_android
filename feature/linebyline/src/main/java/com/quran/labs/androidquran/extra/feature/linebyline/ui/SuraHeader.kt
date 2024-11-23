package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Composable
fun SuraHeader(
  image: ImageBitmap,
  lineId: Int,
  centerX: Float,
  centerY: Float,
  lineRatio: Float,
  tint: Color = Color.Black
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val lastLineIndex = 14
    val lineHeight = this.size.width * lineRatio
    val width = ((this.size.width * 1038) / 1080).toInt()
    val height = (width * image.height) / image.width

    val xOffset = ((centerX * this.size.width) - (width / 2)).toInt()
    val yStart = (this.size.height - lineHeight) / lastLineIndex * lineId
    val yOffset = (yStart + (centerY * lineHeight) - (height / 2)).toInt()

    drawImage(
      image = image,
      dstOffset = IntOffset(xOffset, yOffset),
      dstSize = IntSize(width, height),
      colorFilter = ColorFilter.tint(tint)
    )
  }
}
