package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.util.QuranUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HybridReaderTopPills(
  suraName: String,
  page: Int,
  isPageBookmarked: Boolean,
  onBackClick: () -> Unit,
  onJumpPageClick: () -> Unit,
  onTogglePageBookmark: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenTafsir: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val accent = MaterialTheme.colorScheme.primary
  val editorialAccent = quranColors.editorialAccent
  val textPrimary = MaterialTheme.colorScheme.onBackground
  val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
  val floatBg = quranColors.glassFloatBg.copy(alpha = 1f)
  val floatBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.88f)
  val pillShape = RoundedCornerShape(22.dp)
  val z3Elevation = if (isDark) 12.dp else 10.dp
  val shadowSpot = quranColors.shadowTint.copy(alpha = if (isDark) 0.45f else 0.24f)
  val shadowAmbient = quranColors.shadowTint.copy(alpha = if (isDark) 0.29f else 0.13f)
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val backIcon = if (isRtl) ReaderIcons.ArrowRight else ReaderIcons.ChevronLeft

  Row(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(top = 8.dp, start = 14.dp, end = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Leading Navigation/Title Pill (Z3): > <Surah> | ص <Page>
    Row(
      modifier = Modifier
        .height(36.dp)
        .shadow(
          elevation = z3Elevation,
          shape = pillShape,
          ambientColor = shadowAmbient,
          spotColor = shadowSpot
        )
        .clip(pillShape)
        .background(floatBg)
        .border(width = 1.dp, color = floatBorder, shape = pillShape)
        .semantics(mergeDescendants = true) {}
        .combinedClickable(
          role = Role.Button,
          onClick = onBackClick,
          onLongClick = onJumpPageClick
        )
        .padding(start = 11.dp, end = 13.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = backIcon,
        contentDescription = stringResource(R.string.hybrid_reader_back_cd),
        tint = textPrimary,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = suraName,
        color = textPrimary,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.width(6.dp))
      VerticalHairlineDivider(
        height = 10.dp,
        color = quranColors.divider.copy(alpha = 0.85f)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = stringResource(
          R.string.hybrid_hub_page_pill_format,
          QuranUtils.getLocalizedNumber(page)
        ),
        color = textSecondary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
      )
    }

    // Trailing Tools Pill (Z3): Bookmark (Color 2) | Appearance (textSecondary) | Tafsir (Color 1)
    Row(
      modifier = Modifier
        .height(36.dp)
        .shadow(
          elevation = z3Elevation,
          shape = pillShape,
          ambientColor = shadowAmbient,
          spotColor = shadowSpot
        )
        .clip(pillShape)
        .background(floatBg)
        .border(width = 1.dp, color = floatBorder, shape = pillShape)
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TopToolChip(
        icon = if (isPageBookmarked) ReaderIcons.BookmarkFilled else ReaderIcons.Bookmark,
        label = stringResource(R.string.hybrid_reader_tool_save),
        iconTint = editorialAccent,
        textColor = if (isPageBookmarked) editorialAccent else textPrimary,
        onClick = onTogglePageBookmark
      )
      VerticalHairlineDivider(
        height = 10.dp,
        color = quranColors.divider.copy(alpha = 0.85f),
        modifier = Modifier.padding(horizontal = 2.dp)
      )
      TopToolChip(
        icon = ReaderIcons.Appearance,
        label = stringResource(R.string.hybrid_reader_tool_appearance),
        iconTint = textSecondary,
        textColor = textPrimary,
        onClick = onOpenAppearance
      )
      VerticalHairlineDivider(
        height = 10.dp,
        color = quranColors.divider.copy(alpha = 0.85f),
        modifier = Modifier.padding(horizontal = 2.dp)
      )
      TopToolChip(
        icon = ReaderIcons.Tafsir,
        label = stringResource(R.string.hybrid_reader_tool_tafsir),
        iconTint = accent,
        textColor = textPrimary,
        onClick = onOpenTafsir
      )
    }
  }
}

@Composable
private fun TopToolChip(
  icon: ImageVector,
  label: String,
  iconTint: androidx.compose.ui.graphics.Color,
  textColor: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .height(30.dp)
      .clip(RoundedCornerShape(999.dp))
      .semantics(mergeDescendants = true) {}
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = iconTint,
      modifier = Modifier.size(13.dp)
    )
    Text(
      text = label,
      color = textColor,
      fontSize = 10.5.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1
    )
  }
}
