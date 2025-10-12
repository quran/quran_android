package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val Settings: ImageVector by lazy {
  materialIcon(name = "Filled.Settings") {
    materialPath {
      moveTo(19.14f, 12.94f)
      curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
      curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
      lineToRelative(2.03f, -1.58f)
      curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
      lineToRelative(-1.92f, -3.32f)
      curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
      lineToRelative(-2.39f, 0.96f)
      curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
      lineTo(14.4f, 2.81f)
      curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
      horizontalLineToRelative(-3.84f)
      curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
      lineTo(9.25f, 5.35f)
      curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
      lineTo(5.24f, 5.33f)
      curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
      lineTo(2.74f, 8.87f)
      curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
      lineToRelative(2.03f, 1.58f)
      curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
      reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
      lineToRelative(-2.03f, 1.58f)
      curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
      lineToRelative(1.92f, 3.32f)
      curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
      lineToRelative(2.39f, -0.96f)
      curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
      lineToRelative(0.36f, 2.54f)
      curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
      horizontalLineToRelative(3.84f)
      curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
      lineToRelative(0.36f, -2.54f)
      curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
      lineToRelative(2.39f, 0.96f)
      curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
      lineToRelative(1.92f, -3.32f)
      curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
      lineTo(19.14f, 12.94f)
      close()
      moveTo(12.0f, 15.6f)
      curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
      reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
      reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
      reflectiveCurveTo(13.98f, 15.6f, 12.0f, 15.6f)
      close()
    }
  }
}

@Preview
@Composable
private fun SettingsIconPreview() {
  Icon(imageVector = Settings, contentDescription = null)
}
