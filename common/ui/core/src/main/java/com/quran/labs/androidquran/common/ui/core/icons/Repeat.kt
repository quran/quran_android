package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Repeat: ImageVector by lazy {
  materialIcon(name = "Filled.Repeat") {
    materialPath {
      moveTo(7.0f, 7.0f)
      horizontalLineToRelative(10.0f)
      verticalLineToRelative(3.0f)
      lineToRelative(4.0f, -4.0f)
      lineToRelative(-4.0f, -4.0f)
      verticalLineToRelative(3.0f)
      lineTo(5.0f, 5.0f)
      verticalLineToRelative(6.0f)
      horizontalLineToRelative(2.0f)
      lineTo(7.0f, 7.0f)
      close()
      moveTo(17.0f, 17.0f)
      lineTo(7.0f, 17.0f)
      verticalLineToRelative(-3.0f)
      lineToRelative(-4.0f, 4.0f)
      lineToRelative(4.0f, 4.0f)
      verticalLineToRelative(-3.0f)
      horizontalLineToRelative(12.0f)
      verticalLineToRelative(-6.0f)
      horizontalLineToRelative(-2.0f)
      verticalLineToRelative(4.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun RepeatIconPreview() {
  Icon(imageVector = Repeat, contentDescription = null)
}
