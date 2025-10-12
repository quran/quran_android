package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val FastRewind: ImageVector by lazy {
  materialIcon(name = "Filled.FastRewind") {
    materialPath {
      moveTo(11.0f, 18.0f)
      lineTo(11.0f, 6.0f)
      lineToRelative(-8.5f, 6.0f)
      lineToRelative(8.5f, 6.0f)
      close()
      moveTo(11.5f, 12.0f)
      lineToRelative(8.5f, 6.0f)
      lineTo(20.0f, 6.0f)
      lineToRelative(-8.5f, 6.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun FastRewindIconPreview() {
  Icon(imageVector = FastRewind, contentDescription = null)
}
