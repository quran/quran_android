package com.quran.mobile.feature.pagetheme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.R
import com.quran.mobile.feature.pagetheme.pageAppearanceLabels
import com.quran.mobile.feature.pagetheme.state.PageThemeCard
import com.quran.mobile.feature.pagetheme.state.PageThemeCardBackground
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetEvent
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetState
import kotlin.math.roundToInt

private val originalGradient = Brush.horizontalGradient(
  0f to Color(0xFFDCDAD5),
  0.18f to Color(0xFFFDFDF4),
  0.48f to Color.White,
  1f to Color(0xFFFDFBEF)
)

@Composable
fun PageThemeSheet(state: PageThemeSheetState, onClose: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.prefs_page_theme_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
          .weight(1f)
          .semantics { heading() }
      )
      IconButton(onClick = onClose) {
        Icon(QuranIcons.Close, contentDescription = stringResource(R.string.page_theme_close))
      }
    }

    Spacer(Modifier.size(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      pageAppearanceLabels.forEachIndexed { index, (appearance, label) ->
        SegmentedButton(
          selected = state.pageAppearance == appearance,
          onClick = { state.eventSink(PageThemeSheetEvent.SelectAppearance(appearance)) },
          shape = SegmentedButtonDefaults.itemShape(index, pageAppearanceLabels.size)
        ) {
          Text(stringResource(label))
        }
      }
    }

    Spacer(Modifier.size(16.dp))
    ThemeGrid(state)

    Spacer(Modifier.size(16.dp))
    Box(modifier = Modifier.fillMaxWidth()) {
      BrightnessSliders(state)
      if (state.showOldBackground) {
        OldBackgroundRow(state)
      }
    }
  }
}

@Composable
private fun ThemeGrid(state: PageThemeSheetState) {
  val context = LocalContext.current
  val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val columns = (maxWidth / (104.dp * fontScale)).toInt().coerceIn(1, 3)
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.selectableGroup()
    ) {
      state.cards.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEach { card ->
            ThemeCard(
              name = card.theme.getDisplayName(context),
              card = card,
              onClick = { state.eventSink(PageThemeSheetEvent.SelectTheme(card.theme.storageKey)) },
              modifier = Modifier.weight(1f)
            )
          }
          repeat(columns - row.size) {
            Spacer(Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun ThemeCard(
  name: String,
  card: PageThemeCard,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(12.dp)
  val background = when (val cardBackground = card.background) {
    PageThemeCardBackground.Shaded -> originalGradient
    is PageThemeCardBackground.Solid -> SolidColor(Color(cardBackground.color))
  }
  val textColor = Color(card.textColor)
  val borderColor = if (card.isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outlineVariant
  }
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .heightIn(min = 96.dp)
      .clip(shape)
      .background(background, shape)
      .border(if (card.isSelected) 2.dp else 1.dp, borderColor, shape)
      .selectable(selected = card.isSelected, role = Role.RadioButton, onClick = onClick)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(8.dp)
    ) {
      Text(
        text = stringResource(R.string.page_theme_preview_text),
        color = textColor,
        fontSize = 28.sp,
        modifier = Modifier.clearAndSetSemantics { }
      )
      Text(text = name, color = textColor, style = MaterialTheme.typography.labelLarge)
    }
    if (card.isSelected) {
      Icon(
        imageVector = QuranIcons.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp)
          .size(20.dp)
          .background(MaterialTheme.colorScheme.primary, CircleShape)
          .padding(3.dp)
      )
    }
  }
}

@Composable
private fun BrightnessSliders(state: PageThemeSheetState) {
  val visible = state.showBrightness
  val hidden = if (visible) Modifier else Modifier.clearAndSetSemantics { }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (visible) 1f else 0f)
      .then(hidden)
  ) {
    val textTitle = stringResource(R.string.prefs_night_mode_text_brightness_title)
    Text(text = textTitle, style = MaterialTheme.typography.bodyMedium)
    Slider(
      value = state.textBrightness.toFloat(),
      onValueChange = {
        state.eventSink(PageThemeSheetEvent.TextBrightnessChanged(it.roundToInt()))
      },
      valueRange = PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS.toFloat()..255f,
      enabled = visible,
      modifier = Modifier.semantics { contentDescription = textTitle }
    )
    val backgroundTitle = stringResource(R.string.prefs_night_mode_background_brightness_title)
    Text(text = backgroundTitle, style = MaterialTheme.typography.bodyMedium)
    Slider(
      value = state.backgroundBrightness.toFloat(),
      onValueChange = {
        state.eventSink(PageThemeSheetEvent.BackgroundBrightnessChanged(it.roundToInt()))
      },
      valueRange = 0f..PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS.toFloat(),
      enabled = visible,
      modifier = Modifier.semantics { contentDescription = backgroundTitle }
    )
  }
}

@Composable
private fun OldBackgroundRow(state: PageThemeSheetState) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .toggleable(
        value = state.useOldBackground,
        role = Role.Checkbox,
        onValueChange = { state.eventSink(PageThemeSheetEvent.UseOldBackgroundChanged(it)) }
      )
  ) {
    Checkbox(checked = state.useOldBackground, onCheckedChange = null)
    Spacer(Modifier.width(8.dp))
    Text(text = stringResource(R.string.prefs_old_background_title))
  }
}
