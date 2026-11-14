package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Lightbulb: ImageVector by lazy {
  materialIcon(name = "Filled.Lightbulb") {
    materialPath {
      moveTo(9.0f, 21.0f)
      curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
      horizontalLineToRelative(4.0f)
      curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
      verticalLineToRelative(-1.0f)
      horizontalLineTo(9.0f)
      verticalLineToRelative(1.0f)
      close()
      moveTo(12.0f, 2.0f)
      curveTo(8.14f, 2.0f, 5.0f, 5.14f, 5.0f, 9.0f)
      curveToRelative(0.0f, 2.38f, 1.19f, 4.47f, 3.0f, 5.74f)
      verticalLineTo(17.0f)
      curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
      horizontalLineToRelative(6.0f)
      curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
      verticalLineToRelative(-2.26f)
      curveToRelative(1.81f, -1.27f, 3.0f, -3.36f, 3.0f, -5.74f)
      curveToRelative(0.0f, -3.86f, -3.14f, -7.0f, -7.0f, -7.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun LightbulbIconPreview() {
  Icon(imageVector = Lightbulb, contentDescription = null)
}
