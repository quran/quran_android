package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val PlayArrow: ImageVector by lazy {
  materialIcon(name = "Filled.PlayArrow") {
    materialPath {
      moveTo(8.0f, 5.0f)
      verticalLineToRelative(14.0f)
      lineToRelative(11.0f, -7.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun PlayArrowIconPreview() {
  Icon(imageVector = PlayArrow, contentDescription = null)
}
