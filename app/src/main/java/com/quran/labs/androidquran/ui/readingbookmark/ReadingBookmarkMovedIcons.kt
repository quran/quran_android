package com.quran.labs.androidquran.ui.readingbookmark

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val IconDimension = 24f

// an "x", used to dismiss the first-time reading bookmark education toast
internal val DismissIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "ReadingBookmarkMovedDismiss",
    defaultWidth = IconDimension.dp,
    defaultHeight = IconDimension.dp,
    viewportWidth = IconDimension,
    viewportHeight = IconDimension
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round
    ) {
      moveTo(5f, 5f)
      lineTo(19f, 19f)
      moveTo(19f, 5f)
      lineTo(5f, 19f)
    }
  }.build()
}
