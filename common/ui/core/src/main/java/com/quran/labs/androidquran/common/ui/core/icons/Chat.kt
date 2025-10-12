package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Chat: ImageVector by lazy {
  materialIcon(name = "Filled.Chat", autoMirror = true) {
    materialPath {
      moveTo(20.0f, 2.0f)
      lineTo(4.0f, 2.0f)
      curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
      lineTo(2.0f, 22.0f)
      lineToRelative(4.0f, -4.0f)
      horizontalLineToRelative(14.0f)
      curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
      lineTo(22.0f, 4.0f)
      curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
      close()
      moveTo(6.0f, 9.0f)
      horizontalLineToRelative(12.0f)
      verticalLineToRelative(2.0f)
      lineTo(6.0f, 11.0f)
      lineTo(6.0f, 9.0f)
      close()
      moveTo(14.0f, 14.0f)
      lineTo(6.0f, 14.0f)
      verticalLineToRelative(-2.0f)
      horizontalLineToRelative(8.0f)
      verticalLineToRelative(2.0f)
      close()
      moveTo(18.0f, 8.0f)
      lineTo(6.0f, 8.0f)
      lineTo(6.0f, 6.0f)
      horizontalLineToRelative(12.0f)
      verticalLineToRelative(2.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun ChatIconPreview() {
  Icon(imageVector = Chat, contentDescription = null)
}
