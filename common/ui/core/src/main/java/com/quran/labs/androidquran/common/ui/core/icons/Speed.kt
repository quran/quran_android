package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Speed: ImageVector by lazy {
  materialIcon(name = "Filled.Speed") {
    materialPath {
      moveTo(20.38f, 8.57f)
      lineToRelative(-1.23f, 1.85f)
      arcToRelative(8.0f, 8.0f, 0.0f, false, true, -0.22f, 7.58f)
      lineTo(5.07f, 18.0f)
      arcTo(8.0f, 8.0f, 0.0f, false, true, 15.58f, 6.85f)
      lineToRelative(1.85f, -1.23f)
      arcTo(10.0f, 10.0f, 0.0f, false, false, 3.35f, 19.0f)
      arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.72f, 1.0f)
      horizontalLineToRelative(13.85f)
      arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.74f, -1.0f)
      arcToRelative(10.0f, 10.0f, 0.0f, false, false, -0.27f, -10.44f)
      close()
      moveTo(10.59f, 15.41f)
      arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.83f, 0.0f)
      lineToRelative(5.66f, -8.49f)
      lineToRelative(-8.49f, 5.66f)
      arcToRelative(2.0f, 2.0f, 0.0f, false, false, 0.0f, 2.83f)
      close()
    }
  }
}

@Preview
@Composable
private fun SpeedIconPreview() {
  Icon(imageVector = Speed, contentDescription = null)
}
