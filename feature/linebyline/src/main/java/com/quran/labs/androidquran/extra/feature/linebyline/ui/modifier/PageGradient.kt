package com.quran.labs.androidquran.extra.feature.linebyline.ui.modifier

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

fun Modifier.pageGradient(startWithWidth: Boolean): Modifier {
  val startOffset = if (startWithWidth) Offset(Float.POSITIVE_INFINITY, 0f) else Offset(0f, 0f)
  val endOffset = if (startWithWidth) Offset(0f, 0f) else Offset(Float.POSITIVE_INFINITY, 0f)

  return this.then(
    background(
      brush = Brush.linearGradient(
        0f to Color(0xdc, 0xda, 0xd5),
        0.18f to Color(0xfd, 0xfd, 0xf4),
        0.48f to Color.White,
        1f to Color(0xfd, 0xfb, 0xef),
        start = startOffset,
        end = endOffset,
        tileMode = TileMode.Repeated
      )
    )
  )
}
