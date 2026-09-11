package com.quran.mobile.feature.ayahbookmark.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val IconDimension = 24f

internal val BookmarkIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkGlyph",
    defaultWidth = IconDimension.dp,
    defaultHeight = IconDimension.dp,
    viewportWidth = IconDimension,
    viewportHeight = IconDimension
  ).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(6f, 2f)
      lineTo(18f, 2f)
      lineTo(18f, 22f)
      lineTo(12f, 18f)
      lineTo(6f, 22f)
      close()
    }
  }.build()
}

// a highlighter/pencil nib, used for the "Highlight" card
internal val HighlightIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkHighlight",
    defaultWidth = IconDimension.dp,
    defaultHeight = IconDimension.dp,
    viewportWidth = IconDimension,
    viewportHeight = IconDimension
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round
    ) {
      moveTo(4f, 20f)
      lineTo(9f, 20f)
      lineTo(19f, 10f)
      lineTo(14f, 5f)
      lineTo(4f, 15f)
      close()
      moveTo(14f, 5f)
      lineTo(19f, 10f)
    }
  }.build()
}

// a single diagonal stroke, shown in the "None" swatch while some color is picked
internal val NoHighlightIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkNoHighlight",
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
      moveTo(6f, 18f)
      lineTo(18f, 6f)
    }
  }.build()
}
