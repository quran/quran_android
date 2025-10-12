package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val ExpandMore: ImageVector by lazy {
  materialIcon(name = "Filled.ExpandMore") {
    materialPath {
      moveTo(16.59f, 8.59f)
      lineTo(12.0f, 13.17f)
      lineTo(7.41f, 8.59f)
      lineTo(6.0f, 10.0f)
      lineToRelative(6.0f, 6.0f)
      lineToRelative(6.0f, -6.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ExpandMoreIconPreview() {
  Icon(imageVector = ExpandMore, contentDescription = null)
}
