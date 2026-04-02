package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Search: ImageVector by lazy {
  materialIcon(name = "Filled.Search") {
    materialPath {
      moveTo(15.5f, 14.0f)
      horizontalLineToRelative(-0.79f)
      lineToRelative(-0.28f, -0.27f)
      curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
      curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
      reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
      reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
      curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
      lineToRelative(0.27f, 0.28f)
      verticalLineToRelative(0.79f)
      lineToRelative(5.0f, 4.99f)
      lineTo(20.49f, 19.0f)
      close()
      moveTo(9.5f, 14.0f)
      curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
      reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
      reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
      reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun SearchIconPreview() {
  Icon(imageVector = Search, contentDescription = null)
}
