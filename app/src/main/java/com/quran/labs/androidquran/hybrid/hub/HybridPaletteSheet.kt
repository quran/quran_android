package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.data.Constants

private data class ThemeModeOption(
  val prefValue: String,
  val arrayIndex: Int
)

private val THEME_MODE_OPTIONS = listOf(
  ThemeModeOption(Constants.THEME_DEFAULT, 2),
  ThemeModeOption(Constants.THEME_LIGHT, 0),
  ThemeModeOption(Constants.THEME_DARK, 1)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HybridPaletteSheet(
  currentAppTheme: String,
  currentThemeStyle: AppThemeStyle,
  isDarkActive: Boolean,
  onSelectThemeMode: (String) -> Unit,
  onSelectThemeStyle: (AppThemeStyle) -> Unit,
  onDismiss: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.Transparent,
    contentColor = headingColor,
    shape = RoundedCornerShape(28.dp),
    dragHandle = null
  ) {
    HybridPaletteSheetContent(
      currentAppTheme = currentAppTheme,
      currentThemeStyle = currentThemeStyle,
      isDarkActive = isDarkActive,
      onSelectThemeMode = onSelectThemeMode,
      onSelectThemeStyle = onSelectThemeStyle,
      onDismiss = onDismiss
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HybridPaletteSheetContent(
  currentAppTheme: String,
  currentThemeStyle: AppThemeStyle,
  isDarkActive: Boolean,
  onSelectThemeMode: (String) -> Unit,
  onSelectThemeStyle: (AppThemeStyle) -> Unit,
  onDismiss: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val floatBorder = if (isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.88f)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val sheetShape = RoundedCornerShape(28.dp)
  val modeLabels = stringArrayResource(R.array.themes)
  val styleLabels = stringArrayResource(R.array.theme_styles)
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .padding(bottom = 12.dp + navBottom)
      .shadow(
        elevation = if (isDark) 18.dp else 16.dp,
        shape = sheetShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(sheetShape)
      .background(
        Brush.verticalGradient(
          colors = listOf(
            quranColors.surfaceElevated,
            quranColors.cardSurface
          )
        )
      )
      .border(width = 1.dp, color = floatBorder, shape = sheetShape)
      .padding(horizontal = 20.dp)
      .padding(top = 14.dp, bottom = 20.dp)
      .testTag("hub_palette_sheet"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .size(width = 38.dp, height = 4.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.hybrid_hub_palette_sheet_title),
        color = headingColor,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
      )
      Row(
        modifier = Modifier
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .border(1.dp, cardBorder, CircleShape)
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = onDismiss)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_hub_done),
          color = headingColor,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )
        Icon(
          imageVector = CloseArchitecturalIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp)
        )
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(R.string.hybrid_hub_theme_mode_label),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(CircleShape)
          .background(if (isDark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant)
          .border(1.dp, cardBorder, CircleShape)
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        THEME_MODE_OPTIONS.forEach { option ->
          val selected = currentAppTheme == option.prefValue
          val label = modeLabels.getOrElse(option.arrayIndex) { option.prefValue }
          Box(
            modifier = Modifier
              .weight(1f)
              .then(
                if (selected) {
                  Modifier
                    .shadow(
                      elevation = if (isDark) 2.5.dp else 2.dp,
                      shape = CircleShape,
                      ambientColor = quranColors.ctaBackground,
                      spotColor = quranColors.ctaBackground
                    )
                    .clip(CircleShape)
                    .background(quranColors.ctaBackground)
                } else {
                  Modifier.clip(CircleShape)
                }
              )
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Tab) { onSelectThemeMode(option.prefValue) }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              color = if (selected) quranColors.onCta else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = stringResource(R.string.hybrid_hub_theme_color_label),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AppThemeStyle.entries.forEachIndexed { idx, style ->
          val selected = style == currentThemeStyle
          val previewPalette = style.palette(isDarkActive)
          val label = styleLabels.getOrElse(idx) { style.storageKey }
          val chipBg = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.11f)
              .compositeOver(quranColors.cardSurface)
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          }
          val chipBorder = if (selected) {
            MaterialTheme.colorScheme.primary
          } else {
            cardBorder
          }
          Row(
            modifier = Modifier
              .clip(CircleShape)
              .background(chipBg)
              .border(if (selected) 1.5.dp else 1.dp, chipBorder, CircleShape)
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Button) { onSelectThemeStyle(style) }
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("hub_palette_style_${style.storageKey}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
          ) {
            // Overlapping 2-dot duo-tone swatch (Color 1 + Color 2 with crisp separator ring)
            Box(
              modifier = Modifier
                .width(26.dp)
                .height(16.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Box(
                modifier = Modifier
                  .size(15.dp)
                  .clip(CircleShape)
                  .background(previewPalette.accent)
                  .border(1.5.dp, chipBg, CircleShape)
              )
              Box(
                modifier = Modifier
                  .offset(x = 10.dp)
                  .size(15.dp)
                  .clip(CircleShape)
                  .background(previewPalette.editorialAccent)
                  .border(1.5.dp, chipBg, CircleShape)
              )
            }
            Text(
              text = label,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.5.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1
            )
            if (selected) {
              Icon(
                imageVector = CheckArchitecturalIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(2.dp))
  }
}
