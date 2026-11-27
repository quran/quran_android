package com.quran.labs.androidquran.hybrid.contract

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.util.QuranUtils

@Composable
fun VerticalHairlineDivider(
  modifier: Modifier = Modifier,
  height: Dp = 10.dp,
  color: Color = LocalQuranColors.current.divider.copy(alpha = 0.8f)
) {
  Box(
    modifier = modifier
      .width(1.dp)
      .height(height)
      .background(color)
  )
}

@Composable
fun RubElHizbMedallion(
  number: Int,
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
  numberFormatter: (Int) -> String = QuranUtils::getLocalizedNumber
) {
  val accentColor = MaterialTheme.colorScheme.primary
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val squareSide = this.size.minDimension * (26f / 40f)
          val topLeftOffset = Offset(
            x = (this.size.width - squareSide) / 2f,
            y = (this.size.height - squareSide) / 2f
          )
          val squareSize = Size(squareSide, squareSide)
          val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
          val fillColor = accentColor.copy(alpha = 0.09f)
          val stroke = Stroke(width = 1.25.dp.toPx())
          val strokeColor = accentColor.copy(alpha = 0.70f)
          val pivot = Offset(this.size.width * 0.5f, this.size.height * 0.5f)

          onDrawBehind {
            drawRoundRect(
              color = fillColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner
            )
            drawRoundRect(
              color = strokeColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner,
              style = stroke
            )

            rotate(degrees = 45f, pivot = pivot) {
              drawRoundRect(
                color = fillColor,
                topLeft = topLeftOffset,
                size = squareSize,
                cornerRadius = corner
              )
              drawRoundRect(
                color = strokeColor,
                topLeft = topLeftOffset,
                size = squareSize,
                cornerRadius = corner,
                style = stroke
              )
            }
          }
        }
    )
    Text(
      text = numberFormatter(number),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = accentColor,
      maxLines = 1
    )
  }
}

internal val IndexListIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "HybridIndexList",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
  ).apply {
    path(
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1.9f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
      pathFillType = PathFillType.NonZero
    ) {
      moveTo(8f, 6f)
      lineTo(20f, 6f)
      moveTo(8f, 12f)
      lineTo(20f, 12f)
      moveTo(8f, 18f)
      lineTo(20f, 18f)
    }
    path(
      fill = SolidColor(Color.Black),
      stroke = null,
      pathFillType = PathFillType.NonZero
    ) {
      moveTo(4.5f, 5f)
      arcTo(1f, 1f, 0f, true, true, 4.49f, 5f)
      close()
      moveTo(4.5f, 11f)
      arcTo(1f, 1f, 0f, true, true, 4.49f, 11f)
      close()
      moveTo(4.5f, 17f)
      arcTo(1f, 1f, 0f, true, true, 4.49f, 17f)
      close()
    }
  }.build()
}
