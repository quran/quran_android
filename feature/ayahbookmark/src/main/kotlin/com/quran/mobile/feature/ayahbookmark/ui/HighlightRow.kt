package com.quran.mobile.feature.ayahbookmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.HighlightColorSpec
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.ui.icons.HighlightIcon
import com.quran.mobile.feature.ayahbookmark.ui.icons.NoHighlightIcon

private val SwatchSize = 34.dp

@Composable
internal fun HighlightRow(
  highlight: HighlightColor?,
  onSelect: (HighlightColor) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = ayahBookmarkHighlightColors

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(colors.background)
      .border(1.5.dp, colors.border, RoundedCornerShape(14.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = HighlightIcon,
        contentDescription = null,
        tint = colors.icon,
        modifier = Modifier.size(18.dp)
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 11.dp)
      ) {
        Text(
          text = stringResource(R.string.ayahbookmark_highlight_title),
          style = MaterialTheme.typography.titleSmall,
          color = colors.title
        )
        Text(
          text = stringResource(R.string.ayahbookmark_highlight_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = colors.subtitle
        )
      }
      Text(
        text = highlight.currentLabel(),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = colors.subtitle
      )
    }

    Row(
      verticalAlignment = Alignment.Top,
      modifier = Modifier.padding(top = 10.dp)
    ) {
      NoneSwatch(
        isSelected = highlight == null,
        outlineColor = colors.swatchOutline,
        labelColor = colors.subtitle,
        onClick = onClear,
        modifier = Modifier.weight(1f)
      )
      HighlightColors.sorted.forEach { spec ->
        ColorSwatch(
          spec = spec,
          isSelected = highlight == spec.highlightColor,
          onClick = { onSelect(spec.highlightColor) },
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun HighlightColor?.currentLabel(): String {
  return if (this == null) {
    stringResource(R.string.ayahbookmark_highlight_none)
  } else {
    stringResource(HighlightColors[this].nameResourceId)
  }
}

@Composable
private fun ColorSwatch(
  spec: HighlightColorSpec,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val name = stringResource(spec.nameResourceId)
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = name }
      .padding(vertical = 5.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(SwatchSize)
        .clip(CircleShape)
        .background(spec.color)
    ) {
      if (isSelected) {
        SelectedCheck(tint = HighlightColors.contentColor)
      }
    }
  }
}

@Composable
private fun NoneSwatch(
  isSelected: Boolean,
  outlineColor: Color,
  labelColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val strokeWidth = with(density) { 1.5.dp.toPx() }
  val dashLength = with(density) { 3.dp.toPx() }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      .padding(vertical = 5.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(SwatchSize)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
        .drawBehind {
          drawCircle(
            color = outlineColor,
            radius = (size.minDimension - strokeWidth) / 2,
            style = Stroke(
              width = strokeWidth,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
            )
          )
        }
    ) {
      if (isSelected) {
        SelectedCheck(tint = outlineColor)
      } else {
        Icon(
          imageVector = NoHighlightIcon,
          contentDescription = null,
          tint = outlineColor,
          modifier = Modifier.size(16.dp)
        )
      }
    }
    Text(
      text = stringResource(R.string.ayahbookmark_highlight_none),
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = labelColor,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@Composable
private fun SelectedCheck(tint: Color, modifier: Modifier = Modifier) {
  Icon(
    imageVector = QuranIcons.Check,
    contentDescription = null,
    tint = tint,
    modifier = modifier.size(16.dp)
  )
}
