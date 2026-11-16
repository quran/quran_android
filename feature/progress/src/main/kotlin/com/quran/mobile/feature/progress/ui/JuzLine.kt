package com.quran.mobile.feature.progress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quran.mobile.progress.summary.Held
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * The Quran as one line numbered by its 30 juz: the part read this cycle in the bookmark's color, a
 * ring where it is, and a dashed ring where a steady pace is by the end of today. It runs from the
 * start side (right to left in Arabic). Tapping or dragging along it shows a juz's pages.
 */
@Composable
internal fun JuzLine(
  line: JuzLineState,
  color: Color,
  selectedJuz: Int?,
  onSelect: (Int) -> Unit,
  describe: (Int) -> String,
  modifier: Modifier = Modifier,
) {
  val numbers = rememberNumbers()
  val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
  val surface = MaterialTheme.colorScheme.surface
  val pace = MaterialTheme.colorScheme.primary
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  BoxWithConstraints(modifier.fillMaxWidth()) {
    // on a narrow screen every number doesn't fit: 1, every fifth, and the ones that matter
    val sparse = maxWidth / 30 < 20.dp
    Column {
      Row(Modifier.fillMaxWidth()) {
        for (juz in 1..30) {
          val emphasized = juz == line.current || juz == selectedJuz
          val shown = !sparse || emphasized || juz == 1 || juz % 5 == 0
          Text(
            if (shown) numbers(juz) else "",
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1f),
          )
        }
      }
      Canvas(Modifier.fillMaxWidth().height(32.dp).padding(vertical = 2.dp)) {
        fun x(at: Float) = (at / 30f * size.width).let { if (rtl) size.width - it else it }
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
        val position = line.position
        val start = line.runStart
        if (position != null && start != null && position > start) {
          drawLine(color, Offset(x(start), y), Offset(x(position), y), 10.dp.toPx(), StrokeCap.Round)
        }
        line.pace?.let { drawPaceRing(pace, Offset(x(it), y)) }
        position?.let { drawNowMarker(color, surface, Offset(x(it), y)) }
      }
    }
    // the numbers and the line are one control: a tap or a drag chooses the juz under it, and
    // TalkBack moves through the juz as along a slider
    Box(Modifier.matchParentSize().juzControl(selectedJuz ?: line.current ?: 1, rtl, onSelect, describe))
  }
}

/** The juz at [x] of a row [width] wide (numbered from the start side). */
internal fun juzAt(x: Float, width: Int, rtl: Boolean): Int {
  val fraction = (x / width).coerceIn(0f, 0.9999f)
  return 1 + ((if (rtl) 1f - fraction else fraction) * 30).toInt().coerceIn(0, 29)
}

/** One control over the 30 juz: tap or drag to choose one; for TalkBack, adjustable like a slider. */
private fun Modifier.juzControl(current: Int, rtl: Boolean, onSelect: (Int) -> Unit, describe: (Int) -> String): Modifier =
  this
    .pointerInput(rtl) { detectTapGestures { onSelect(juzAt(it.x, size.width, rtl)) } }
    .pointerInput(rtl) { detectHorizontalDragGestures { change, _ -> onSelect(juzAt(change.position.x, size.width, rtl)) } }
    .semantics {
      contentDescription = describe(current)
      progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 1f..30f, steps = 28)
      setProgress { value ->
        onSelect(value.roundToInt().coerceIn(1, 30))
        true
      }
    }

private fun DrawScope.drawNowMarker(color: Color, surface: Color, center: Offset) {
  drawCircle(surface, radius = 12.dp.toPx(), center = center)
  drawCircle(color, radius = 9.dp.toPx(), center = center, style = Stroke(6.dp.toPx()))
}

private fun DrawScope.drawPaceRing(color: Color, center: Offset) {
  drawCircle(
    color,
    radius = 11.dp.toPx(),
    center = center,
    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
  )
}

/** The juz line's key: its marks drawn as on the line, each with its meaning. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JuzLegend(color: Color, showPace: Boolean, now: String, pace: String, read: String, modifier: Modifier = Modifier) {
  val surface = MaterialTheme.colorScheme.surface
  val paceColor = MaterialTheme.colorScheme.primary
  FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    LegendItem(now) { drawNowMarker(color, surface, Offset(size.width / 2, size.height / 2)) }
    if (showPace) LegendItem(pace) { drawPaceRing(paceColor, Offset(size.width / 2, size.height / 2)) }
    LegendItem(read) {
      val y = size.height / 2
      drawLine(color, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), 6.dp.toPx(), StrokeCap.Round)
    }
  }
}

@Composable
private fun LegendItem(text: String, mark: DrawScope.() -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(26.dp), onDraw = mark)
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 6.dp),
    )
  }
}

/**
 * Ayat held in each of the 30 juz, numbered like the juz line: a bar for the share held, with how
 * many. A juz is a button that lists its suras.
 */
@Composable
internal fun HeldChart(
  held: ImmutableList<Held>,
  color: Color,
  selectedJuz: Int?,
  onSelect: (Int) -> Unit,
  describe: (Held) -> String,
  panel: Color,
  faded: Boolean,
  modifier: Modifier = Modifier,
) {
  val numbers = rememberNumbers()
  val stub = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  val height = 120.dp
  BoxWithConstraints(modifier.fillMaxWidth()) {
  // counts that aren't 0; on a narrow screen, the numbers of 1, every fifth and the chosen juz
  val sparse = maxWidth / 30 < 20.dp
  Row(Modifier.fillMaxWidth()) {
    held.forEach { juz ->
      val share = if (juz.ayat == 0) 0f else juz.held.toFloat() / juz.ayat
      val selected = juz.number == selectedJuz
      Column(
        Modifier
          .weight(1f)
          // a background, not a clip: the counts and numbers may be wider than a narrow column
          .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(Modifier.fillMaxWidth().height(height).padding(horizontal = 2.dp), contentAlignment = Alignment.BottomCenter) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              if (juz.held == 0) "" else numbers(juz.held),
              style = MaterialTheme.typography.labelSmall.tabular(),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Visible,
              modifier = Modifier.background(if (selected) Color.Transparent else panel),
            )
            val bar = if (juz.held == 0) 2.dp else ((height - 16.dp) * share).coerceAtLeast(3.dp)
            val tint = if (juz.held == 0) stub else color
            Box(
              Modifier
                .fillMaxWidth()
                .height(bar)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(if (faded) tint.copy(alpha = 0.45f) else tint)
            )
          }
        }
        Text(
          if (!sparse || selected || juz.number == 1 || juz.number % 5 == 0) numbers(juz.number) else "",
          style = MaterialTheme.typography.labelSmall.tabular(),
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
          color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Visible,
          modifier = Modifier.padding(vertical = 3.dp),
        )
      }
    }
  }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(Modifier.matchParentSize().juzControl(selectedJuz ?: 30, rtl, onSelect) { number -> describe(held[number - 1]) })
  }
}
