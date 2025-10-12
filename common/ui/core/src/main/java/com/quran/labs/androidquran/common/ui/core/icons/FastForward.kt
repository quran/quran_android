package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val FastForward: ImageVector by lazy {
  materialIcon(name = "Filled.FastForward") {
    materialPath {
      moveTo(4.0f, 18.0f)
      lineToRelative(8.5f, -6.0f)
      lineTo(4.0f, 6.0f)
      verticalLineToRelative(12.0f)
      close()
      moveTo(13.0f, 6.0f)
      verticalLineToRelative(12.0f)
      lineToRelative(8.5f, -6.0f)
      lineTo(13.0f, 6.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun FastForwardIconPreview() {
  Icon(imageVector = FastForward, contentDescription = null)
}
