package com.quran.labs.androidquran.extra.feature.linebyline.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

@Composable
fun QuranHeaderFooter(
  left: String,
  right: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  // always render the footer as LTR, otherwise, the page number will be rendered
  // on the wrong side of the page for RTL due to Row being smart and flipping the
  // children based on LTR/RTL. We want this flipping for the header and footer
  // so that the page number is always on the right side for odd numbered
  // pages and always on the left side for even numbered pages, and likewise with
  // the juz' number and sura name.
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints {
      val fontSize = (min(this.maxWidth, 480.dp) * 0.0345f).value.sp
      Row(modifier = modifier.padding(vertical = 2.dp)) {
        val textStyle = MaterialTheme.typography.subtitle2.copy(fontSize = fontSize)
        Text(left, style = textStyle, color = color)
        Spacer(modifier = Modifier.weight(1f, true))
        Text(right, style = textStyle, color = color)
      }
    }
  }
}
