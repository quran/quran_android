package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Logout: ImageVector by lazy {
  materialIcon(name = "Filled.Logout", autoMirror = true) {
    materialPath {
      moveTo(17.0f, 7.0f)
      lineToRelative(-1.41f, 1.41f)
      lineTo(18.17f, 11.0f)
      horizontalLineTo(8.0f)
      verticalLineToRelative(2.0f)
      horizontalLineToRelative(10.17f)
      lineToRelative(-2.58f, 2.59f)
      lineTo(17.0f, 17.0f)
      lineToRelative(5.0f, -5.0f)
      close()
      moveTo(4.0f, 5.0f)
      horizontalLineToRelative(8.0f)
      verticalLineTo(3.0f)
      horizontalLineTo(4.0f)
      curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
      verticalLineToRelative(14.0f)
      curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
      horizontalLineToRelative(8.0f)
      verticalLineToRelative(-2.0f)
      horizontalLineTo(4.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun LogoutIconPreview() {
  Icon(imageVector = Logout, contentDescription = null)
}
