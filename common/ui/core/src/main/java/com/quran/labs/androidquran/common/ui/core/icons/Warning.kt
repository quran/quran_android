package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Warning: ImageVector by lazy {
  materialIcon(name = "Outlined.Warning") {
    materialPath {
      moveTo(12.0f, 5.99f)
      lineTo(19.53f, 19.0f)
      horizontalLineTo(4.47f)
      lineTo(12.0f, 5.99f)
      moveTo(12.0f, 2.0f)
      lineTo(1.0f, 21.0f)
      horizontalLineToRelative(22.0f)
      lineTo(12.0f, 2.0f)
      close()
      moveTo(13.0f, 16.0f)
      horizontalLineToRelative(-2.0f)
      verticalLineToRelative(2.0f)
      horizontalLineToRelative(2.0f)
      verticalLineTo(16.0f)
      close()
      moveTo(13.0f, 10.0f)
      horizontalLineToRelative(-2.0f)
      verticalLineToRelative(4.0f)
      horizontalLineToRelative(2.0f)
      verticalLineTo(10.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun WarningIconPreview() {
  Icon(imageVector = Warning, contentDescription = null)
}
