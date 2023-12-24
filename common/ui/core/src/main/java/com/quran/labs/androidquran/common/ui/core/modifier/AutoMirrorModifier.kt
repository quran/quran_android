package com.quran.labs.androidquran.common.ui.core.modifier

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// via https://stackoverflow.com/questions/69849631/auto-mirroring-in-jetpack-compose-icons
@Stable
fun Modifier.autoMirror(): Modifier = composed {
  if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
    this.scale(scaleX = -1f, scaleY = 1f)
  } else { this }
}
