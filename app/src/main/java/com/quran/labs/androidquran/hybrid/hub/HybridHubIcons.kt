package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val ARCH_STROKE_WIDTH = 1.65f

/**
 * Overlapping-circles monochrome architectural appearance icon (`1.65dp` stroke, right circle
 * filled at 22% alpha) matching `HybridArchitecturalIcons.APPEARANCE_FILLED`.
 */
internal val AppearanceFilledIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridAppearanceFilled",
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
      moveTo(4f, 12f)
      arcTo(5.5f, 5.5f, 0f, true, false, 15f, 12f)
      arcTo(5.5f, 5.5f, 0f, true, false, 4f, 12f)
      close()
    }
    path(
      fill = SolidColor(Color.Black),
      fillAlpha = 0.22f,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = ARCH_STROKE_WIDTH,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
      pathFillType = PathFillType.NonZero
    ) {
      moveTo(9f, 12f)
      arcTo(5.5f, 5.5f, 0f, true, false, 20f, 12f)
      arcTo(5.5f, 5.5f, 0f, true, false, 9f, 12f)
      close()
    }
  }.build()
}

internal val PaletteIcon: ImageVector
  get() = AppearanceFilledIcon

internal val SettingsArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridSettingsArchitectural",
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
      moveTo(9f, 12f)
      arcTo(3f, 3f, 0f, true, false, 15f, 12f)
      arcTo(3f, 3f, 0f, true, false, 9f, 12f)
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
      moveTo(19.4f, 15f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, 1.82f)
      lineToRelative(0.06f, 0.06f)
      arcToRelative(2f, 2f, 0f, true, true, -2.83f, 2.83f)
      lineToRelative(-0.06f, -0.06f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, -0.33f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, 1.51f)
      verticalLineTo(21f)
      arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
      verticalLineToRelative(-0.09f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, -1.51f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, 0.33f)
      lineToRelative(-0.06f, 0.06f)
      arcToRelative(2f, 2f, 0f, true, true, -2.83f, -2.83f)
      lineToRelative(0.06f, -0.06f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, -1.82f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, -1f)
      horizontalLineTo(3f)
      arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
      horizontalLineToRelative(0.09f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, -1f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, -1.82f)
      lineToRelative(-0.06f, -0.06f)
      arcToRelative(2f, 2f, 0f, true, true, 2.83f, -2.83f)
      lineToRelative(0.06f, 0.06f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, 0.33f)
      horizontalLineToRelative(0.08f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 0.92f, -1.51f)
      verticalLineTo(3f)
      arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
      verticalLineToRelative(0.09f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, 1.51f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, -0.33f)
      lineToRelative(0.06f, -0.06f)
      arcToRelative(2f, 2f, 0f, true, true, 2.83f, 2.83f)
      lineToRelative(-0.06f, 0.06f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, 1.82f)
      verticalLineToRelative(0.08f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, 0.92f)
      horizontalLineTo(21f)
      arcToRelative(2f, 2f, 0f, true, true, 0f, 4f)
      horizontalLineToRelative(-0.09f)
      arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, 1f)
      close()
    }
  }.build()
}

internal val BookArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridBookArchitectural",
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

internal val BookmarkArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridBookmarkArchitectural",
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

internal val BookmarkFilledArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridBookmarkFilledArchitectural",
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

internal val ChevronLeftArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridChevronLeftArchitectural",
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

internal val CheckArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridCheckArchitectural",
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
      moveTo(4.5f, 12.5f)
      lineTo(9.5f, 17.5f)
      lineTo(19.5f, 7.5f)
    }
  }.build()
}

internal val CloseArchitecturalIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridCloseArchitectural",
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

internal val ArrowForwardAutoMirror: ImageVector
  get() = ChevronLeftArchitecturalIcon
