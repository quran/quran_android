package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Stop: ImageVector by lazy {
  materialIcon(name = "Filled.Stop") {
    materialPath {
      moveTo(6.0f, 6.0f)
      horizontalLineToRelative(12.0f)
      verticalLineToRelative(12.0f)
      horizontalLineTo(6.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun StopIconPreview() {
  Icon(imageVector = Stop, contentDescription = null)
}
