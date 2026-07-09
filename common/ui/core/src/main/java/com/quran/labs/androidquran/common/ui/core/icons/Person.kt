package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Person: ImageVector by lazy {
  materialIcon(name = "Filled.Person") {
    materialPath {
      moveTo(12.0f, 12.0f)
      curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
      reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
      reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
      reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
      close()
      moveTo(12.0f, 14.0f)
      curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
      verticalLineToRelative(2.0f)
      horizontalLineToRelative(16.0f)
      verticalLineToRelative(-2.0f)
      curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun PersonIconPreview() {
  Icon(imageVector = Person, contentDescription = null)
}
