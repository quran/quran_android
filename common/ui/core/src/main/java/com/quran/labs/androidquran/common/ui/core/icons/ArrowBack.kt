package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val ArrowBack: ImageVector by lazy {
  materialIcon(name = "Filled.ArrowBack", autoMirror = true) {
    materialPath {
      moveTo(20.0f, 11.0f)
      horizontalLineTo(7.83f)
      lineToRelative(5.59f, -5.59f)
      lineTo(12.0f, 4.0f)
      lineToRelative(-8.0f, 8.0f)
      lineToRelative(8.0f, 8.0f)
      lineToRelative(1.41f, -1.41f)
      lineTo(7.83f, 13.0f)
      horizontalLineTo(20.0f)
      verticalLineToRelative(-2.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ArrowBackIconPreview() {
  Icon(imageVector = ArrowBack, contentDescription = null)
}
