package com.quran.mobile.linebyline.ui.renderer.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun QuranLineLayout(
  lineHeightWidthRatio: Float,
  modifier: Modifier = Modifier,
  allowLinesToOverlap: Boolean = true,
  content: @Composable () -> Unit
) {
  Layout(modifier = modifier, content = content) { measurables, constraints ->
    // this is the size of each line (respecting the aspect ratio) if we ignore height constraints.
    val lineHeightGivenWidth = (constraints.maxWidth * lineHeightWidthRatio).toInt()

    val lineHeight = if (allowLinesToOverlap) {
      // use the height as is and trust that the programmer is intentionally allowing
      // overlap of images in some cases because it makes sense.
      lineHeightGivenWidth
    } else {
      // lines aren't allowed to overlap, so split the height into equal sections
      constraints.maxHeight / (max(measurables.size, 1))
    }

    val placeables = measurables.map { measurable ->
      val lineConstraints = constraints.copy(minHeight = lineHeight, maxHeight = lineHeight)
      measurable.measure(lineConstraints)
    }

    // casting to float so the y ends up a float so we can floor and round to int below
    val lastLineIndex = max(placeables.size - 1, 1).toFloat()
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeables.forEachIndexed { index, placeable ->
        val y = floor((constraints.maxHeight - lineHeight) / lastLineIndex * index).roundToInt()
        placeable.placeRelative(0, y)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun QuranPageLayoutPreview() {
  MaterialTheme {
    QuranLineLayout(
      lineHeightWidthRatio = 174f / 1080f,
      allowLinesToOverlap = true,
      modifier = Modifier.fillMaxSize()
    ) {
      repeat(15) {
        val color = if (it % 2 == 0) Color(0xaaff0000) else Color(0xaa0000ff)
        Box(modifier = Modifier.background(color))
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun QuranPageLayoutOverridingPreview() {
  MaterialTheme {
    QuranLineLayout(lineHeightWidthRatio = 174f / 1080f, modifier = Modifier.fillMaxSize()) {
      repeat(15) {
        val color = if (it % 2 == 0) Color(0xaaff0000) else Color(0xaa0000ff)
        Box(modifier = Modifier.background(color))
      }
    }
  }
}
