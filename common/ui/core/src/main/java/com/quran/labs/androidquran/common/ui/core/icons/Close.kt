package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Close: ImageVector by lazy {
  materialIcon(name = "Filled.Close") {
    materialPath {
      moveTo(19.0f, 6.41f)
      lineTo(17.59f, 5.0f)
      lineTo(12.0f, 10.59f)
      lineTo(6.41f, 5.0f)
      lineTo(5.0f, 6.41f)
      lineTo(10.59f, 12.0f)
      lineTo(5.0f, 17.59f)
      lineTo(6.41f, 19.0f)
      lineTo(12.0f, 13.41f)
      lineTo(17.59f, 19.0f)
      lineTo(19.0f, 17.59f)
      lineTo(13.41f, 12.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun CloseIconPreview() {
  Icon(imageVector = Close, contentDescription = null)
}
