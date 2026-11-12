package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val ErrorOutline: ImageVector by lazy {
  materialIcon(name = "Outlined.ErrorOutline") {
    materialPath {
      moveTo(11.0f, 15.0f)
      horizontalLineToRelative(2.0f)
      verticalLineToRelative(2.0f)
      horizontalLineToRelative(-2.0f)
      close()
      moveTo(11.0f, 7.0f)
      horizontalLineToRelative(2.0f)
      verticalLineToRelative(6.0f)
      horizontalLineToRelative(-2.0f)
      close()
      moveTo(11.99f, 2.0f)
      curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
      reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
      curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
      reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
      close()
      moveTo(12.0f, 20.0f)
      curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
      reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
      reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
      reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ErrorOutlineIconPreview() {
  Icon(imageVector = ErrorOutline, contentDescription = null)
}
