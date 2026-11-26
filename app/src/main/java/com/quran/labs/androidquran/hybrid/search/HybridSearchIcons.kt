package com.quran.labs.androidquran.hybrid.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Monochrome `1.65dp` architectural stroke icons for Calm Rust Search Compose mirror,
 * matching `HybridArchitecturalIcons` 1:1.
 */
internal object HybridSearchIcons {
  private const val ARCH_STROKE_WIDTH = 1.65f

  val Search: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchMagnifier",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(4.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, true, false, 17.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, true, false, 4.5f, 11f)
        close()
        moveTo(20f, 20f)
        lineTo(15.8f, 15.8f)
      }
    }.build()
  }

  val Close: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchClose",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(17f, 7f)
        lineTo(7f, 17f)
        moveTo(7f, 7f)
        lineTo(17f, 17f)
      }
    }.build()
  }

  val Clock: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchClock",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(3.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, false, 20.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, true, false, 3.5f, 12f)
        close()
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(15f, 14f)
      }
    }.build()
  }

  val Play: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchPlay",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(8f, 5.5f)
        lineTo(19f, 12f)
        lineTo(8f, 18.5f)
        close()
      }
    }.build()
  }

  val Bolt: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchBolt",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(13.5f, 2f)
        lineTo(5f, 13.5f)
        horizontalLineTo(11.5f)
        lineTo(10.5f, 22f)
        lineTo(19f, 10.5f)
        horizontalLineTo(12.5f)
        close()
      }
    }.build()
  }

  val BookmarkOutline: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchBookmarkOutline",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(6.5f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 8f, 3f)
        horizontalLineTo(16f)
        arcTo(1.5f, 1.5f, 0f, false, true, 17.5f, 4.5f)
        verticalLineTo(21f)
        lineTo(12f, 17.4f)
        lineTo(6.5f, 21f)
        close()
      }
    }.build()
  }

  val BookmarkFilled: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchBookmarkFilled",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(6.5f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 8f, 3f)
        horizontalLineTo(16f)
        arcTo(1.5f, 1.5f, 0f, false, true, 17.5f, 4.5f)
        verticalLineTo(21f)
        lineTo(12f, 17.4f)
        lineTo(6.5f, 21f)
        close()
      }
    }.build()
  }

  val OpenPageArrow: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchOpenPage",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
      }
    }.build()
  }

  val Book: ImageVector by lazy {
    ImageVector.Builder(
      name = "HybridSearchBook",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(2f, 5.5f)
        arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 3f)
        horizontalLineTo(9f)
        arcTo(3f, 3f, 0f, false, true, 12f, 6f)
        verticalLineTo(20.5f)
        arcTo(2.2f, 2.2f, 0f, false, false, 9.8f, 18.3f)
        horizontalLineTo(4.5f)
        arcTo(2.5f, 2.5f, 0f, false, false, 2f, 20.5f)
        close()
      }
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ARCH_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(22f, 5.5f)
        arcTo(2.5f, 2.5f, 0f, false, false, 19.5f, 3f)
        horizontalLineTo(15f)
        arcTo(3f, 3f, 0f, false, false, 12f, 6f)
        verticalLineTo(20.5f)
        arcTo(2.2f, 2.2f, 0f, false, true, 14.2f, 18.3f)
        horizontalLineTo(19.5f)
        arcTo(2.5f, 2.5f, 0f, false, true, 22f, 20.5f)
        close()
      }
    }.build()
  }
}
