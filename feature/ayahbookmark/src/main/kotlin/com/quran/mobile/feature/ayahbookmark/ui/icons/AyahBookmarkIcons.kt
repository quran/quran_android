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

// a trash can with a lid, used for the destructive "Remove bookmark" action
internal val RemoveBookmarkIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkRemoveBookmark",
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
      moveTo(4f, 7f)
      lineTo(20f, 7f)
      moveTo(9f, 7f)
      lineTo(9f, 5f)
      curveTo(9f, 4.45f, 9.45f, 4f, 10f, 4f)
      lineTo(14f, 4f)
      curveTo(14.55f, 4f, 15f, 4.45f, 15f, 5f)
      lineTo(15f, 7f)
      moveTo(6f, 7f)
      lineTo(7f, 20f)
      curveTo(7f, 20.55f, 7.45f, 21f, 8f, 21f)
      lineTo(16f, 21f)
      curveTo(16.55f, 21f, 17f, 20.55f, 17f, 20f)
      lineTo(18f, 7f)
    }
  }.build()
}

// a plain trash can (no lid), used in the "Bookmark removed" undo toast
internal val RemovedIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkRemoved",
    defaultWidth = IconDimension.dp,
    defaultHeight = IconDimension.dp,
    viewportWidth = IconDimension,
    viewportHeight = IconDimension
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2.4f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round
    ) {
      moveTo(4f, 7f)
      lineTo(20f, 7f)
      moveTo(6f, 7f)
      lineTo(7f, 20f)
      lineTo(17f, 20f)
      lineTo(18f, 7f)
    }
  }.build()
}

// an outlined circle with an exclamation mark, used for the amber "last place" notice
internal val WarningIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "AyahBookmarkWarning",
    defaultWidth = IconDimension.dp,
    defaultHeight = IconDimension.dp,
    viewportWidth = IconDimension,
    viewportHeight = IconDimension
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2.2f,
      strokeLineCap = StrokeCap.Round
    ) {
      moveTo(21f, 12f)
      curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
      curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
      curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
      curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
      close()
      moveTo(12f, 8f)
      lineTo(12f, 13f)
      moveTo(12f, 16.4f)
      lineTo(12f, 16.5f)
    }
  }.build()
}
