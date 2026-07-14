package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val BookmarkBorder: ImageVector by lazy {
  materialIcon(name = "Outlined.BookmarkBorder") {
    materialPath {
      moveTo(17.0f, 3.0f)
      horizontalLineTo(7.0f)
      curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
      lineTo(5.0f, 21.0f)
      lineToRelative(7.0f, -3.0f)
      lineToRelative(7.0f, 3.0f)
      verticalLineTo(5.0f)
      curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
      close()
      moveTo(17.0f, 18.0f)
      lineToRelative(-5.0f, -2.18f)
      lineTo(7.0f, 18.0f)
      verticalLineTo(5.0f)
      horizontalLineTo(17.0f)
      verticalLineTo(18.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun BookmarkBorderIconPreview() {
  Icon(imageVector = BookmarkBorder, contentDescription = null)
}
