package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val ArrowCircleDown: ImageVector by lazy {
  materialIcon(name = "Outlined.ArrowCircleDown") {
    materialPath {
      moveTo(12.0f, 4.0f)
      curveToRelative(4.41f, 0.0f, 8.0f, 3.59f, 8.0f, 8.0f)
      reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
      reflectiveCurveToRelative(-8.0f, -3.59f, -8.0f, -8.0f)
      reflectiveCurveTo(7.59f, 4.0f, 12.0f, 4.0f)
      moveTo(12.0f, 2.0f)
      curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
      curveToRelative(0.0f, 5.52f, 4.48f, 10.0f, 10.0f, 10.0f)
      curveToRelative(5.52f, 0.0f, 10.0f, -4.48f, 10.0f, -10.0f)
      curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
      lineTo(12.0f, 2.0f)
      close()
      moveTo(13.0f, 12.0f)
      lineToRelative(0.0f, -4.0f)
      horizontalLineToRelative(-2.0f)
      lineToRelative(0.0f, 4.0f)
      horizontalLineTo(8.0f)
      lineToRelative(4.0f, 4.0f)
      lineToRelative(4.0f, -4.0f)
      horizontalLineTo(13.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ArrowCircleDownIconPreview() {
  Icon(imageVector = ArrowCircleDown, contentDescription = null)
}
