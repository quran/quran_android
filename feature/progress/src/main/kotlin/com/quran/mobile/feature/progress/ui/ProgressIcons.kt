package com.quran.mobile.feature.progress.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icons the app's shared set doesn't have (paths from Material Icons, Apache-2.0). */
internal object ProgressIcons {
  val Bookmark: ImageVector by lazy {
    icon("Bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")
  }

  val Add: ImageVector by lazy {
    icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
  }

  /** Memorizing: a book. */
  val Memorizing: ImageVector by lazy {
    icon(
      "AutoStories",
      "M19,1l-5,5v11l5,-4.5V1zM1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20" +
        "c1.95,0 4.05,0.4 5.5,1.5V6c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6zM23,19.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1" +
        "v13.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5v2c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05" +
        "c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5v-1.1z",
    )
  }

  /** Salah. */
  val Mosque: ImageVector by lazy {
    icon(
      "Mosque",
      "M7,8h10c0.29,0,0.57,0.06,0.84,0.13C17.93,7.8,18,7.46,18,7.09c0-1.31-0.65-2.53-1.74-3.25L12,1L7.74,3.84" +
        " C6.65,4.56,6,5.78,6,7.09C6,7.46,6.07,7.8,6.16,8.13C6.43,8.06,6.71,8,7,8z",
      "M24,7c0-1.1-2-3-2-3s-2,1.9-2,3c0,0.74,0.4,1.38,1,1.72V13h-2v-2c0-1.1-0.9-2-2-2H7c-1.1,0-2,0.9-2,2v2H3V8.72" +
        " C3.6,8.38,4,7.74,4,7c0-1.1-2-3-2-3S0,5.9,0,7c0,0.74,0.4,1.38,1,1.72V21h9v-4c0-1.1,0.9-2,2-2s2,0.9,2,2v4h9V8.72" +
        " C23.6,8.38,24,7.74,24,7z",
    )
  }

  /** Sessions: a list by date. */
  val Sessions: ImageVector by lazy {
    icon(
      "EventNote",
      "M17,10L7,10v2h10v-2zM19,3h-1L18,1h-2v2L8,3L8,1L6,1v2L5,3c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14" +
        "c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19L5,19L5,8h14v11zM14,14L7,14v2h7v-2z",
    )
  }

  val PlayArrow: ImageVector by lazy { icon("PlayArrow", "M8,5v14l11,-7z") }

  private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
      .apply { paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) } }
      .build()
}
