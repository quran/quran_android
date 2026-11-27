package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors

@Composable
internal fun HybridHubHeader(
  onPaletteClick: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val floatBorder = if (isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.88f)
  val editorialSoftBg = quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = stringResource(R.string.hybrid_app_title),
      color = headingColor,
      fontSize = 21.sp,
      fontWeight = FontWeight.Bold,
      lineHeight = 27.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .testTag("hub_header_title")
    )

    // Floating Z2 capsule pill housing Appearance (Color 2) + Settings (monochrome)
    Row(
      modifier = Modifier
        .shadow(
          elevation = if (isDark) 7.dp else 6.dp,
          shape = CircleShape,
          ambientColor = quranColors.shadowTint,
          spotColor = quranColors.shadowTint
        )
        .clip(CircleShape)
        .background(quranColors.glassFloatBg)
        .border(width = 1.dp, color = floatBorder, shape = CircleShape)
        .padding(horizontal = 5.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HeaderCapsuleIconButton(
        icon = AppearanceFilledIcon,
        contentDescription = stringResource(R.string.hybrid_hub_palette_desc),
        containerColor = editorialSoftBg,
        iconTint = quranColors.editorialAccent,
        onClick = onPaletteClick,
        modifier = Modifier.testTag("hub_palette_button")
      )
      HeaderCapsuleIconButton(
        icon = SettingsArchitecturalIcon,
        contentDescription = stringResource(R.string.hybrid_hub_settings_desc),
        containerColor = Color.Transparent,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onSettingsClick,
        modifier = Modifier.testTag("hub_settings_button")
      )
    }
  }
}

@Composable
private fun HeaderCapsuleIconButton(
  icon: ImageVector,
  contentDescription: String,
  containerColor: Color,
  iconTint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .size(32.dp)
      .clip(CircleShape)
      .background(containerColor)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = iconTint,
      modifier = Modifier.size(16.dp)
    )
  }
}
