package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val HighlightOff: ImageVector by lazy {
  materialIcon(name = "Filled.HighlightOff") {
    materialPath {
      moveTo(14.59f, 8.0f)
      lineTo(12.0f, 10.59f)
      lineTo(9.41f, 8.0f)
      lineTo(8.0f, 9.41f)
      lineTo(10.59f, 12.0f)
      lineTo(8.0f, 14.59f)
      lineTo(9.41f, 16.0f)
      lineTo(12.0f, 13.41f)
      lineTo(14.59f, 16.0f)
      lineTo(16.0f, 14.59f)
      lineTo(13.41f, 12.0f)
      lineTo(16.0f, 9.41f)
      lineTo(14.59f, 8.0f)
      close()
      moveTo(12.0f, 2.0f)
      curveTo(6.47f, 2.0f, 2.0f, 6.47f, 2.0f, 12.0f)
      reflectiveCurveToRelative(4.47f, 10.0f, 10.0f, 10.0f)
      reflectiveCurveToRelative(10.0f, -4.47f, 10.0f, -10.0f)
      reflectiveCurveTo(17.53f, 2.0f, 12.0f, 2.0f)
      close()
      moveToRelative(0.0f, 18.0f)
      curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
      reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
      reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
      reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun HighlightOffIconPreview() {
  Icon(imageVector = HighlightOff, contentDescription = null)
}
