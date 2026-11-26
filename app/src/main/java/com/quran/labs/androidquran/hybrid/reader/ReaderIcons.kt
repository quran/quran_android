package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Strictly monochrome SF-Symbols-inspired architectural stroke icons (`1.65dp` in a 24x24 viewport,
 * ROUND cap & join) mirrored in lockstep with `HybridArchitecturalIcons` and
 * `floating_layers_theme_proposal.html`.
 */
internal object ReaderIcons {
  private const val STROKE = 1.65f

  val Appearance: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderAppearance",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(4f, 12f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 15f, 12f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4f, 12f)
        close()
      }
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(9f, 12f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 20f, 12f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9f, 12f)
        close()
      }
    }.build()
  }

  val ArrowRight: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderArrowRight",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
      }
    }.build()
  }

  val ChevronLeft: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderChevronLeft",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
      }
    }.build()
  }

  val Bookmark: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderBookmark",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6.5f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 3f)
        horizontalLineTo(16f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 4.5f)
        verticalLineTo(21f)
        lineTo(12f, 17.4f)
        lineTo(6.5f, 21f)
        close()
      }
    }.build()
  }

  val BookmarkFilled: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderBookmarkFilled",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6.5f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 3f)
        horizontalLineTo(16f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 4.5f)
        verticalLineTo(21f)
        lineTo(12f, 17.4f)
        lineTo(6.5f, 21f)
        close()
      }
    }.build()
  }

  val Tafsir: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderTafsir",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(4f, 4.5f)
        arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.5f, 2f)
        horizontalLineTo(19f)
        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 3f)
        verticalLineTo(20f)
        arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
        horizontalLineTo(6.5f)
        arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 18.5f)
        close()
        moveTo(8.5f, 7.5f); lineTo(15.5f, 7.5f)
        moveTo(8.5f, 11.5f); lineTo(15.5f, 11.5f)
        moveTo(8.5f, 15.5f); lineTo(12.5f, 15.5f)
      }
    }.build()
  }

  val Play: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderPlay",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(8f, 5.5f)
        lineTo(19f, 12f)
        lineTo(8f, 18.5f)
        close()
      }
    }.build()
  }

  val Pause: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderPause",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(7.5f, 5.5f)
        horizontalLineTo(9.5f)
        arcTo(1f, 1f, 0f, false, true, 10.5f, 6.5f)
        verticalLineTo(17.5f)
        arcTo(1f, 1f, 0f, false, true, 9.5f, 18.5f)
        horizontalLineTo(7.5f)
        arcTo(1f, 1f, 0f, false, true, 6.5f, 17.5f)
        verticalLineTo(6.5f)
        arcTo(1f, 1f, 0f, false, true, 7.5f, 5.5f)
        close()
        moveTo(14.5f, 5.5f)
        horizontalLineTo(16.5f)
        arcTo(1f, 1f, 0f, false, true, 17.5f, 6.5f)
        verticalLineTo(17.5f)
        arcTo(1f, 1f, 0f, false, true, 16.5f, 18.5f)
        horizontalLineTo(14.5f)
        arcTo(1f, 1f, 0f, false, true, 13.5f, 17.5f)
        verticalLineTo(6.5f)
        arcTo(1f, 1f, 0f, false, true, 14.5f, 5.5f)
        close()
      }
    }.build()
  }

  val Stop: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderStop",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(8.5f, 6.5f)
        horizontalLineTo(15.5f)
        arcTo(2f, 2f, 0f, false, true, 17.5f, 8.5f)
        verticalLineTo(15.5f)
        arcTo(2f, 2f, 0f, false, true, 15.5f, 17.5f)
        horizontalLineTo(8.5f)
        arcTo(2f, 2f, 0f, false, true, 6.5f, 15.5f)
        verticalLineTo(8.5f)
        arcTo(2f, 2f, 0f, false, true, 8.5f, 6.5f)
        close()
      }
    }.build()
  }

  val Copy: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderCopy",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(11f, 9f)
        horizontalLineTo(18f)
        arcTo(2f, 2f, 0f, false, true, 20f, 11f)
        verticalLineTo(18f)
        arcTo(2f, 2f, 0f, false, true, 18f, 20f)
        horizontalLineTo(11f)
        arcTo(2f, 2f, 0f, false, true, 9f, 18f)
        verticalLineTo(11f)
        arcTo(2f, 2f, 0f, false, true, 11f, 9f)
        close()
        moveTo(5f, 15f)
        horizontalLineTo(4f)
        arcTo(2f, 2f, 0f, false, true, 2f, 13f)
        verticalLineTo(4f)
        arcTo(2f, 2f, 0f, false, true, 4f, 2f)
        horizontalLineTo(13f)
        arcTo(2f, 2f, 0f, false, true, 15f, 4f)
        verticalLineTo(5f)
      }
    }.build()
  }

  val Share: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderShare",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(4f, 12f)
        verticalLineTo(19f)
        arcTo(2f, 2f, 0f, false, false, 6f, 21f)
        horizontalLineTo(18f)
        arcTo(2f, 2f, 0f, false, false, 20f, 19f)
        verticalLineTo(12f)
        moveTo(16f, 6f); lineTo(12f, 2f); lineTo(8f, 6f)
        moveTo(12f, 2f); lineTo(12f, 15f)
      }
    }.build()
  }

  val Close: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderClose",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(17f, 7f); lineTo(7f, 17f)
        moveTo(7f, 7f); lineTo(17f, 17f)
      }
    }.build()
  }

  val Index: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderIndex",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(8f, 6.5f); lineTo(20f, 6.5f)
        moveTo(8f, 12f); lineTo(20f, 12f)
        moveTo(8f, 17.5f); lineTo(20f, 17.5f)
      }
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(3.5f, 6.5f); arcTo(1f, 1f, 0f, true, false, 5.5f, 6.5f); arcTo(1f, 1f, 0f, true, false, 3.5f, 6.5f); close()
        moveTo(3.5f, 12f); arcTo(1f, 1f, 0f, true, false, 5.5f, 12f); arcTo(1f, 1f, 0f, true, false, 3.5f, 12f); close()
        moveTo(3.5f, 17.5f); arcTo(1f, 1f, 0f, true, false, 5.5f, 17.5f); arcTo(1f, 1f, 0f, true, false, 3.5f, 17.5f); close()
      }
    }.build()
  }

  val Check: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderCheck",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 7.5f)
      }
    }.build()
  }

  val Waveform: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderWaveform",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(4f, 10f); lineTo(4f, 14f)
        moveTo(8f, 7f); lineTo(8f, 17f)
        moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(16f, 7f); lineTo(16f, 17f)
        moveTo(20f, 10f); lineTo(20f, 14f)
      }
    }.build()
  }

  val ChevronUp: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderChevronUp",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6f, 15f)
        lineTo(12f, 9f)
        lineTo(18f, 15f)
      }
    }.build()
  }

  val ChevronDown: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderChevronDown",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
      }
    }.build()
  }

  val SkipNext: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderSkipNext",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(18f, 6f); lineTo(18f, 18f)
      }
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6f, 6.5f)
        lineTo(14.5f, 12f)
        lineTo(6f, 17.5f)
        close()
      }
    }.build()
  }

  val SkipPrev: ImageVector by lazy {
    ImageVector.Builder(
      name = "ReaderSkipPrev",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6f, 6f); lineTo(6f, 18f)
      }
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(18f, 6.5f)
        lineTo(9.5f, 12f)
        lineTo(18f, 17.5f)
        close()
      }
    }.build()
  }
}
