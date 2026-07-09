package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val ChevronRight: ImageVector by lazy {
  materialIcon(name = "Filled.ChevronRight", autoMirror = true) {
    materialPath {
      moveTo(10.0f, 6.0f)
      lineTo(8.59f, 7.41f)
      lineTo(13.17f, 12.0f)
      lineToRelative(-4.58f, 4.59f)
      lineTo(10.0f, 18.0f)
      lineToRelative(6.0f, -6.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ChevronRightIconPreview() {
  Icon(imageVector = ChevronRight, contentDescription = null)
}
