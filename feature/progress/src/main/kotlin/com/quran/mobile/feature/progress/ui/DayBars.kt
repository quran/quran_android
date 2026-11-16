package com.quran.mobile.feature.progress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

private val LabelHeight = 16.dp
private val AxisHeight = 22.dp
private val MinColumn = 14.dp
// the width a day gets when the chart scrolls (a phone)
private val ScrollColumn = 22.dp

/**
 * A bar's color: by day today's is a shade darker; by night (where a lighter shade reads as faded)
 * the other days are a little dimmer instead.
 */
internal fun Color.forDay(today: Boolean, dark: Boolean): Color = when {
  dark && !today -> copy(alpha = alpha * 0.72f)
  !dark && today -> lerp(this, Color.Black, 0.35f)
  else -> this
}

/**
 * Days side by side on one numbered axis: a value above it ([DayColumn.above], e.g. pages) and one
 * hanging below it ([DayColumn.below], e.g. minutes), each bar with its value written on it. Today is
 * a shade darker with its number in bold; days still to come are dashed outlines. Lines across the
 * chart mark a goal above and an average below. The days run from the start side, so right to left in
 * Arabic; when they don't fit, the chart scrolls, opening at today.
 *
 * @param describe what TalkBack says for a day.
 * @param panel the color behind the chart, which the values are written on.
 * @param faded draws the bars faded, for sample days.
 */
@Composable
internal fun DayBars(
  columns: ImmutableList<DayColumn>,
  aboveColor: Color,
  belowColor: Color?,
  aboveLine: Int?,
  belowLine: Int?,
  describe: (DayColumn) -> String,
  panel: Color,
  modifier: Modifier = Modifier,
  faded: Boolean = false,
  dark: Boolean = false,
  aboveHeight: Dp = 170.dp,
  belowHeight: Dp = 130.dp,
) {
  if (columns.isEmpty()) return
  val numbers = rememberNumbers()
  val aboveScale = maxOf(columns.maxOf { it.above ?: 0 }, aboveLine ?: 0, 1) * 1.1f
  val belowScale = maxOf(columns.maxOf { it.below ?: 0 }, belowLine ?: 0, 1) * 1.1f
  val lineColor = MaterialTheme.colorScheme.outline
  val stub = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  val showBelow = belowColor != null
  val totalHeight = aboveHeight + AxisHeight + if (showBelow) belowHeight else 0.dp

  BoxWithConstraints(modifier.fillMaxWidth()) {
    val fits = maxWidth / columns.size >= MinColumn
    val density = LocalDensity.current
    val today = columns.indexOfFirst { it.isToday }
    // it opens with today in the middle (or as far as it goes: the scroll stops at the end)
    val scroll = remember(today, maxWidth) {
      val column = with(density) { ScrollColumn.toPx() }
      val viewport = with(density) { maxWidth.toPx() }
      // whole days: the first one shown starts at the edge
      val first = if (today < 0) 0 else ((today + 1) - (viewport / column / 2).toInt()).coerceAtLeast(0)
      ScrollState((first * column).toInt())
    }
    val chart = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(scroll).width(ScrollColumn * columns.size)
    Box(chart.height(totalHeight)) {
      // the goal and average lines, under the bars and their values
      Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val stroke = 1.5.dp.toPx()
        val aboveBars = (aboveHeight - LabelHeight).toPx()
        aboveLine?.let { goal ->
          val y = aboveHeight.toPx() - goal / aboveScale * aboveBars
          drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke, pathEffect = dash)
        }
        if (showBelow) {
          belowLine?.let { average ->
            val belowBars = (belowHeight - LabelHeight).toPx()
            val y = (aboveHeight + AxisHeight).toPx() + average / belowScale * belowBars
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke, pathEffect = dash)
          }
        }
      }
      Row(Modifier.fillMaxSize()) {
        columns.forEach { column ->
          val cell = if (fits) Modifier.weight(1f) else Modifier.width(ScrollColumn)
          val description = describe(column)
          Column(
            cell.clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            val above = aboveColor.forDay(column.isToday, dark)
            Box(Modifier.fillMaxWidth().height(aboveHeight).padding(horizontal = 2.dp), contentAlignment = Alignment.BottomCenter) {
              val value = column.above
              if (value == null) {
                ToCome(Modifier.fillMaxSize().padding(top = LabelHeight))
              } else if (!column.empty) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  BarValue(numbers(value), panel, column.isToday)
                  Bar(value, aboveScale, aboveHeight - LabelHeight, if (value == 0) stub else above, faded, top = true)
                }
              }
            }
            Box(Modifier.height(AxisHeight), contentAlignment = Alignment.Center) {
              Text(
                numbers(column.label),
                style = MaterialTheme.typography.labelSmall.tabular(),
                fontWeight = if (column.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (column.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
              )
            }
            if (belowColor != null) {
              val below = belowColor.forDay(column.isToday, dark)
              Box(Modifier.fillMaxWidth().height(belowHeight).padding(horizontal = 2.dp), contentAlignment = Alignment.TopCenter) {
                val value = column.below
                if (value == null) {
                  ToCome(Modifier.fillMaxSize().padding(bottom = LabelHeight))
                } else if (!column.empty) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Bar(value, belowScale, belowHeight - LabelHeight, if (value == 0) stub else below, faded, top = false)
                    BarValue(numbers(value), panel, column.isToday)
                  }
                }
              }
            }
          }
        }
      }
    }
    if (!fits) {
      // more days that way: the edge fades
      val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
      if (scroll.canScrollBackward) EdgeFade(panel, Modifier.align(Alignment.CenterStart).height(totalHeight), fromLeft = !rtl)
      if (scroll.canScrollForward) EdgeFade(panel, Modifier.align(Alignment.CenterEnd).height(totalHeight), fromLeft = rtl)
    }
  }
}

@Composable
private fun BarValue(text: String, panel: Color, today: Boolean) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall.tabular(),
    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Visible,
    modifier = Modifier.height(LabelHeight).background(panel),
  )
}

/** A bar [value] of [scale] tall in [room]; a sliver for 0. */
@Composable
private fun Bar(value: Int, scale: Float, room: Dp, color: Color, faded: Boolean, top: Boolean) {
  val height = if (value == 0) 2.dp else (room * (value / scale)).coerceAtLeast(3.dp)
  val corner = 3.dp
  val shape = if (top) RoundedCornerShape(topStart = corner, topEnd = corner) else RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
  Box(
    Modifier
      .fillMaxWidth()
      .height(height)
      .clip(shape)
      .background(if (faded) color.copy(alpha = color.alpha * 0.45f) else color)
  )
}

/** A fade from the panel's color at the edge of a scrolled chart, where more days lie. */
@Composable
private fun EdgeFade(panel: Color, modifier: Modifier, fromLeft: Boolean) {
  val colors = listOf(panel, panel.copy(alpha = 0f))
  Box(modifier.width(28.dp).background(Brush.horizontalGradient(if (fromLeft) colors else colors.reversed())))
}

/** A day still to come: a faint dashed outline where its bar will be. */
@Composable
private fun ToCome(modifier: Modifier) {
  val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  Box(
    modifier.drawBehind {
      drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
      )
    }
  )
}
