package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val MenuBook: ImageVector by lazy {
  materialIcon(name = "Filled.MenuBook", autoMirror = true) {
    materialPath {
      moveTo(21.0f, 5.0f)
      curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
      curveToRelative(-1.95f, 0.0f, -4.05f, 0.4f, -5.5f, 1.5f)
      curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
      reflectiveCurveTo(2.45f, 4.9f, 1.0f, 6.0f)
      verticalLineToRelative(14.65f)
      curveToRelative(0.0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
      curveToRelative(0.1f, 0.0f, 0.15f, -0.05f, 0.25f, -0.05f)
      curveTo(3.1f, 20.45f, 5.05f, 20.0f, 6.5f, 20.0f)
      curveToRelative(1.95f, 0.0f, 4.05f, 0.4f, 5.5f, 1.5f)
      curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
      curveToRelative(1.65f, 0.0f, 3.35f, 0.3f, 4.75f, 1.05f)
      curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
      curveToRelative(0.25f, 0.0f, 0.5f, -0.25f, 0.5f, -0.5f)
      verticalLineTo(6.0f)
      curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21.0f, 5.0f)
      close()
      moveTo(21.0f, 18.5f)
      curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
      curveToRelative(-1.7f, 0.0f, -4.15f, 0.65f, -5.5f, 1.5f)
      verticalLineTo(8.0f)
      curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
      curveToRelative(1.2f, 0.0f, 2.4f, 0.15f, 3.5f, 0.5f)
      verticalLineTo(18.5f)
      close()
    }
    materialPath {
      moveTo(17.5f, 10.5f)
      curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
      verticalLineTo(9.24f)
      curveTo(19.21f, 9.09f, 18.36f, 9.0f, 17.5f, 9.0f)
      curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
      verticalLineToRelative(1.66f)
      curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
      close()
    }
    materialPath {
      moveTo(13.0f, 12.49f)
      verticalLineToRelative(1.66f)
      curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
      curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
      verticalLineTo(11.9f)
      curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
      curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13.0f, 12.49f)
      close()
    }
    materialPath {
      moveTo(17.5f, 14.33f)
      curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
      verticalLineToRelative(1.66f)
      curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
      curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
      verticalLineToRelative(-1.52f)
      curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
      close()
    }
  }
}

@Preview
@Composable
private fun MenuBookIconPreview() {
  Icon(imageVector = MenuBook, contentDescription = null)
}
