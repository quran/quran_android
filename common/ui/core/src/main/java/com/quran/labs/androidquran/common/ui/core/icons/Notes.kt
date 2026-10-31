package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Notes: ImageVector by lazy {
  materialIcon(name = "Filled.Notes", autoMirror = true) {
    materialPath {
      moveTo(3.0f, 18.0f)
      horizontalLineToRelative(12.0f)
      verticalLineToRelative(-2.0f)
      horizontalLineTo(3.0f)
      verticalLineToRelative(2.0f)
      close()
      moveTo(3.0f, 6.0f)
      verticalLineToRelative(2.0f)
      horizontalLineToRelative(18.0f)
      verticalLineTo(6.0f)
      horizontalLineTo(3.0f)
      close()
      moveToRelative(0.0f, 7.0f)
      horizontalLineToRelative(18.0f)
      verticalLineToRelative(-2.0f)
      horizontalLineTo(3.0f)
      verticalLineToRelative(2.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun NotesIconPreview() {
  Icon(imageVector = Notes, contentDescription = null)
}
