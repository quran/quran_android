package com.quran.mobile.linebyline.ui.renderer.composable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.quran.mobile.linebyline.ui.renderer.calculator.PageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranScrollablePageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.SidelinesWrapperCalculator

@Composable
fun QuranScrollablePage(
  page: Int,
  scrollState: ScrollState,
  header: @Composable () -> Unit,
  quran: @Composable () -> Unit,
  footer: @Composable () -> Unit,
  showSidelines: Boolean,
  sidelines: @Composable () -> Unit
) {
  val innerCalculator = QuranScrollablePageCalculator(LocalDensity.current.density)
  val calculator = if (showSidelines) {
    SidelinesWrapperCalculator(innerCalculator)
  } else {
    innerCalculator
  }

  Layout(
    content = {
      QuranScrollableColumn(
        page,
        calculator = calculator,
        scrollState = scrollState,
        header = { header() },
        quran = { quran() },
        footer = { footer() },
        showSidelines = showSidelines,
        sidelines = { sidelines() }
      )
    }
  ) { measurables, constraints ->
    // the page should occupy the entire screen
    val pagePlaceable = measurables[0].measure(constraints)

    layout(constraints.maxWidth, constraints.maxHeight) {
      val xOffset = (constraints.maxWidth - pagePlaceable.width) / 2
      pagePlaceable.place(xOffset, 0, 0f)
    }
  }
}

@Composable
fun QuranScrollableColumn(
  page: Int,
  calculator: PageCalculator,
  scrollState: ScrollState,
  header: @Composable () -> Unit,
  quran: @Composable () -> Unit,
  footer: @Composable () -> Unit,
  showSidelines: Boolean,
  sidelines: @Composable () -> Unit
) {
  val screenHeight = with(LocalDensity.current) {
    LocalConfiguration.current.screenHeightDp.dp.toPx()
  }

  val currentVerticalPadding = WindowInsets.systemBars
    .union(WindowInsets.navigationBars)
    .union(WindowInsets.displayCutout)
    .only(WindowInsetsSides.Vertical)
    .asPaddingValues()

  val verticalPadding = remember { mutableIntStateOf(0) }
  val maximumVerticalPadding = max(
    currentVerticalPadding.calculateTopPadding(),
    currentVerticalPadding.calculateBottomPadding()
  )

  val maximumVerticalPaddingPx = with(LocalDensity.current) { maximumVerticalPadding.roundToPx() }

  if (maximumVerticalPaddingPx > verticalPadding.intValue) {
    verticalPadding.intValue = maximumVerticalPaddingPx
  }

  Layout(
    content = {
      header()
      quran()
      footer()
      if (showSidelines) {
        sidelines()
      }
    },
    modifier = Modifier.verticalScroll(scrollState)
  ) { measurables, constraints ->
    val maxHeight = if (constraints.hasBoundedHeight) {
      constraints.maxHeight
    } else {
      screenHeight.toInt()
    }

    val measurements = calculator.calculate(constraints.maxWidth, maxHeight)
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

    val contentInset = (0.5 * measurements.headerFooterHeight).toInt()

    val nonSidelinesWidth = constraints.maxWidth - measurements.sidelinesWidth
    val sidelinesStartDelta = if (showSidelines && page % 2 == 1) measurements.sidelinesWidth else 0

    val extraVerticalPadding = verticalPadding.intValue
    val totalMeasuredHeight = 2 * extraVerticalPadding + contentInset +
      headerPlaceable.measuredHeight + pagePlaceable.measuredHeight + footerPlaceable.measuredHeight
    layout(constraints.maxWidth, totalMeasuredHeight) {
      val headerFooterHorizontal = sidelinesStartDelta + ((nonSidelinesWidth - headerPlaceable.width) / 2)
      headerPlaceable.place(headerFooterHorizontal, 0 + extraVerticalPadding)
      val pageHorizontal = sidelinesStartDelta + ((nonSidelinesWidth - pagePlaceable.width) / 2)
      pagePlaceable.place(pageHorizontal, headerPlaceable.measuredHeight + extraVerticalPadding)

      val footerY = totalMeasuredHeight - (contentInset + footerPlaceable.measuredHeight)
      footerPlaceable.place(headerFooterHorizontal, footerY - extraVerticalPadding)

      if (sidelinesPlaceable != null) {
        val sidelinesHorizontal = if (sidelinesStartDelta > 0) {
          0
        } else {
          constraints.maxWidth - sidelinesPlaceable.width
        }
        sidelinesPlaceable.place(sidelinesHorizontal, headerPlaceable.measuredHeight + extraVerticalPadding)
      }
    }
  }
}

@Preview
@Composable
fun QuranScrollablePagePreview() {
  MaterialTheme {
    QuranScrollablePage(
      page = 1,
      rememberScrollState(),
      header = {
        Text("Header", modifier = Modifier.background(color = Color(0xe8, 0xf5, 0xe9)))
      },
      quran = {
        Text("Quran", modifier = Modifier.background(color = Color(0x90, 0xa4, 0xae)))
      },
      footer = {
        Text("Footer", modifier = Modifier.background(color = Color(0xe8, 0xf5, 0xe9)))
      },
      sidelines = {},
      showSidelines = false
    )
  }
}
