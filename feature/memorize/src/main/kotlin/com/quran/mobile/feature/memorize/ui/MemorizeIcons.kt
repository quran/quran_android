package com.quran.mobile.feature.memorize.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icons the app's shared set doesn't have (paths from Material Icons, Apache-2.0). */
internal object MemorizeIcons {
  val Delete: ImageVector by lazy {
    icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
  }

  val Export: ImageVector by lazy {
    icon("Export", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
  }

  val MicOff: ImageVector by lazy {
    icon(
      "MicOff",
      "M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28zM14.98,11.17" +
        "c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v0.18l5.98,5.99zM4.27,3L3,4.27" +
        "l6.01,6.01L9.01,11c0,1.66 1.33,3 2.99,3 0.22,0 0.44,-0.03 0.65,-0.08l1.66,1.66c-0.71,0.33 -1.5,0.52" +
        " -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c0.91,-0.13 1.77," +
        "-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z"
    )
  }

  private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
      .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
      .build()
}
