package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Pause: ImageVector by lazy {
  materialIcon(name = "Filled.Pause") {
    materialPath {
      moveTo(6.0f, 19.0f)
      horizontalLineToRelative(4.0f)
      lineTo(10.0f, 5.0f)
      lineTo(6.0f, 5.0f)
      verticalLineToRelative(14.0f)
      close()
      moveTo(14.0f, 5.0f)
      verticalLineToRelative(14.0f)
      horizontalLineToRelative(4.0f)
      lineTo(18.0f, 5.0f)
      horizontalLineToRelative(-4.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun PauseIconPreview() {
  Icon(imageVector = Pause, contentDescription = null)
}
