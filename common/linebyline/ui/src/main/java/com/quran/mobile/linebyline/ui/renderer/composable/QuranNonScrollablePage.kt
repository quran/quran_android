package com.quran.mobile.linebyline.ui.renderer.composable

import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranPageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.SidelinesWrapperCalculator

@Composable
fun QuranNonScrollablePage(
  page: Int,
  header: @Composable () -> Unit,
  quran: @Composable () -> Unit,
  footer: @Composable () -> Unit,
  showSidelines: Boolean,
  sidelines: @Composable () -> Unit
) {
  val innerCalculator = QuranPageCalculator()
  val calculator = if (showSidelines) {
    SidelinesWrapperCalculator(innerCalculator)
  } else {
    innerCalculator
  }

  Layout(
    content = {
      header()
      quran()
      footer()
      if (showSidelines) {
        sidelines()
      }
    }
  ) { measurables, constraints ->
    val measurements = calculator.calculate(constraints.maxWidth, constraints.maxHeight)
    val headerFooterMeasurements = constraints.copy(
      minWidth = measurements.headerFooterWidth,
      maxWidth = measurements.headerFooterWidth,
      minHeight = measurements.headerFooterHeight,
      maxHeight = measurements.headerFooterHeight
    )
    val pageMeasurements = constraints.copy(
      minWidth = measurements.quranImageWidth,
      maxWidth = measurements.quranImageWidth,
      minHeight = measurements.quranImageHeight,
      maxHeight = measurements.quranImageHeight
    )

    val headerPlaceable = measurables[0].measure(headerFooterMeasurements)
    val pagePlaceable = measurables[1].measure(pageMeasurements)
    val footerPlaceable = measurables[2].measure(headerFooterMeasurements)
    val sidelinesPlaceable = if (showSidelines) {
      val sidelinesMeasurements = pageMeasurements.copy(
        minWidth = measurements.sidelinesWidth,
        maxWidth = measurements.sidelinesWidth
      )
      measurables[3].measure(sidelinesMeasurements)
    } else {
      null
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
      val nonSidelinesWidth = constraints.maxWidth - measurements.sidelinesWidth
      val sidelinesStartDelta = if (showSidelines && page % 2 == 1) measurements.sidelinesWidth else 0
      val headerTop =
        (constraints.maxHeight - headerPlaceable.height - pagePlaceable.height - headerPlaceable.height) / 2
      val headerHorizontal = sidelinesStartDelta + (nonSidelinesWidth - headerPlaceable.width) / 2
      headerPlaceable.place(headerHorizontal, headerTop)
      val pageHorizontal = sidelinesStartDelta + (nonSidelinesWidth - pagePlaceable.width) / 2
      pagePlaceable.place(pageHorizontal, headerTop + headerPlaceable.measuredHeight)
      footerPlaceable.place(
        headerHorizontal,
        headerTop + headerPlaceable.measuredHeight + pagePlaceable.measuredHeight
      )

      if (sidelinesPlaceable != null) {
        val sidelinesHorizontal = if (sidelinesStartDelta > 0) {
          0
        } else {
          constraints.maxWidth - sidelinesPlaceable.width
        }
        sidelinesPlaceable.place(sidelinesHorizontal, headerTop + headerPlaceable.measuredHeight)
      }
    }
  }
}

@Preview
@Composable
fun QuranPagePreview() {
  MaterialTheme {
    QuranNonScrollablePage(
      page = 1,
      header = {
        Text("Header", modifier = Modifier.background(color = Color(0xe8, 0xf5, 0xe9)))
      },
      quran = {
        Text("Quran", modifier = Modifier.background(color = Color(0x90, 0xa4, 0xae)))
      },
      footer = {
        Text("Footer", modifier = Modifier.background(color = Color(0xe8, 0xf5, 0xe9)))
      },
      showSidelines = false,
      sidelines = {}
    )
  }
}
