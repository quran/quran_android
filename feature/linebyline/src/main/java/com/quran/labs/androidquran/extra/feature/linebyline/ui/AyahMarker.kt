package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
fun AyahMarker(
  lineId: Int,
  centerX: Float,
  centerY: Float,
  ringColor: Color,
  innerColor: Color,
  text: String,
  textColor: Color,
  textWidthRatio: Float
) {
  val textMeasurer = rememberTextMeasurer()

  Canvas(modifier = Modifier.fillMaxSize()) {
    val width = this.size.width
    val strokeWidth = 0.003f * width
    val markerDimen = (0.025f * 2 * width).toInt()
    val radius = markerDimen / 2f

    val lastLineIndex = 14
    val lineHeight = this.size.width * 174 / 1080

    val xOffset = ((centerX * this.size.width) - (markerDimen / 2))
    val yStart = (this.size.height - lineHeight) / lastLineIndex * lineId
    val yOffset = (yStart + (centerY * lineHeight) - (markerDimen / 2))

    drawCircle(innerColor, radius, center = Offset(xOffset + radius, yOffset + radius))
    drawCircle(
      color = ringColor,
      radius = radius,
      center = Offset(xOffset + radius, yOffset + radius),
      style = Stroke(width = strokeWidth)
    )

    val fontSize = (width * textWidthRatio).toSp()
    val measurement =
      textMeasurer.measure(text, TextStyle.Default.copy(color = textColor, fontSize = fontSize))

    drawText(
      measurement,
      topLeft = Offset(
        xOffset + (radius - (measurement.size.width / 2)),
        yOffset + (radius - (measurement.size.height / 2)) + 4 - 1
      )
    )
  }
}
