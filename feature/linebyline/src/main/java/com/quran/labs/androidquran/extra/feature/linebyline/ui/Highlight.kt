package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun Highlight(lineId: Int, left: Float, right: Float, lineRatio: Float, color: Color) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val highlightWidth = ceil((right - left) * this.size.width)

    val lineHeight = this.size.width * lineRatio
    val lastLineIndex = 14f
    val lineHeightWithoutOverlap = (this.size.height - lineHeight) / lastLineIndex

    val yStart = (lineHeight - lineHeightWithoutOverlap) / 2
    val y = (yStart + lineHeightWithoutOverlap * lineId)

    val x = left * this.size.width
    drawRect(color, Offset(x, y), Size(highlightWidth, lineHeightWithoutOverlap))
  }
}
