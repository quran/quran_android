package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.util.QuranUtils
import kotlin.math.roundToInt

internal const val TAG_FLOATING_AYAH_CARET = "hybrid_floating_ayah_caret"

@Composable
fun FloatingAyahActionBar(
  placement: AyahActionPlacement,
  suraName: String,
  selectedStart: SuraAyah,
  selectedEnd: SuraAyah?,
  isBookmarked: Boolean,
  onListenClick: () -> Unit,
  onTafsirClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onCopyClick: () -> Unit,
  onShareClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val accent = MaterialTheme.colorScheme.primary
  val editorialAccent = quranColors.editorialAccent
  val isDark = !quranColors.isLightToolbar
  val pillBg = quranColors.glassFloatBg.copy(alpha = 1f)
  val pillBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.88f)
  val pillShape = RoundedCornerShape(22.dp)
  val density = LocalDensity.current

  val tagText = if (selectedEnd != null && selectedEnd != selectedStart && selectedEnd.sura == selectedStart.sura) {
    stringResource(
      R.string.hybrid_reader_selection_range_tag_format,
      suraName,
      QuranUtils.getLocalizedNumber(selectedStart.ayah),
      QuranUtils.getLocalizedNumber(selectedEnd.ayah)
    )
  } else {
    stringResource(
      R.string.hybrid_reader_selection_tag_format,
      suraName,
      QuranUtils.getLocalizedNumber(selectedStart.ayah)
    )
  }

  val caretWidthPx = with(density) { 12.dp.toPx() }
  val cornerSafetyPx = with(density) { 24.dp.toPx() }
  val minCaretOffsetXPx = placement.pillLeftPx + cornerSafetyPx
  val maxCaretOffsetXPx = (placement.pillLeftPx + placement.pillWidthPx - cornerSafetyPx - caretWidthPx)
    .coerceAtLeast(minCaretOffsetXPx)
  val caretOffsetXPx = (placement.caretCenterXPx - caretWidthPx * 0.5f)
    .coerceIn(minCaretOffsetXPx, maxCaretOffsetXPx)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .offset { IntOffset(0, placement.topPx.roundToInt()) },
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (placement.direction == AyahBarPlacementDirection.BELOW_AYAH) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f),
          contentAlignment = AbsoluteAlignment.TopLeft
        ) {
          AyahCaret(
            pointingUp = true,
            fillColor = pillBg,
            strokeColor = pillBorder,
            modifier = Modifier
              .absoluteOffset { IntOffset(caretOffsetXPx.roundToInt(), 1) }
              .testTag(TAG_FLOATING_AYAH_CARET)
          )
        }
      }
      AyahActionPillRow(
        pillBg = pillBg,
        pillBorder = pillBorder,
        pillShape = pillShape,
        isDark = isDark,
        shadowTint = quranColors.shadowTint,
        accent = accent,
        editorialAccent = editorialAccent,
        textColor = MaterialTheme.colorScheme.onBackground,
        dividerColor = quranColors.divider.copy(alpha = 0.7f),
        isBookmarked = isBookmarked,
        onListenClick = onListenClick,
        onTafsirClick = onTafsirClick,
        onBookmarkClick = onBookmarkClick,
        onCopyClick = onCopyClick,
        onShareClick = onShareClick
      )
      if (placement.showSelectionTag) {
        Spacer(modifier = Modifier.height(8.dp))
        SelectionTagPill(tagText = tagText)
      }
    } else {
      if (placement.showSelectionTag) {
        SelectionTagPill(tagText = tagText)
        Spacer(modifier = Modifier.height(8.dp))
      }
      AyahActionPillRow(
        pillBg = pillBg,
        pillBorder = pillBorder,
        pillShape = pillShape,
        isDark = isDark,
        shadowTint = quranColors.shadowTint,
        accent = accent,
        editorialAccent = editorialAccent,
        textColor = MaterialTheme.colorScheme.onBackground,
        dividerColor = quranColors.divider.copy(alpha = 0.7f),
        isBookmarked = isBookmarked,
        onListenClick = onListenClick,
        onTafsirClick = onTafsirClick,
        onBookmarkClick = onBookmarkClick,
        onCopyClick = onCopyClick,
        onShareClick = onShareClick
      )
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f),
          contentAlignment = AbsoluteAlignment.TopLeft
        ) {
          AyahCaret(
            pointingUp = false,
            fillColor = pillBg,
            strokeColor = pillBorder,
            modifier = Modifier
              .absoluteOffset { IntOffset(caretOffsetXPx.roundToInt(), -1) }
              .testTag(TAG_FLOATING_AYAH_CARET)
          )
        }
      }
    }
  }
}

@Composable
private fun AyahActionPillRow(
  pillBg: Color,
  pillBorder: Color,
  pillShape: RoundedCornerShape,
  isDark: Boolean,
  shadowTint: Color,
  accent: Color,
  editorialAccent: Color,
  textColor: Color,
  dividerColor: Color,
  isBookmarked: Boolean,
  onListenClick: () -> Unit,
  onTafsirClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onCopyClick: () -> Unit,
  onShareClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp)
      .shadow(
        elevation = if (isDark) 12.dp else 10.dp,
        shape = pillShape,
        ambientColor = shadowTint.copy(alpha = if (isDark) 0.29f else 0.13f),
        spotColor = shadowTint.copy(alpha = if (isDark) 0.45f else 0.24f)
      )
      .clip(pillShape)
      .background(pillBg)
      .border(width = 1.dp, color = pillBorder, shape = pillShape)
      .padding(vertical = 8.dp, horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AyahActionCell(
      icon = ReaderIcons.Play,
      label = stringResource(R.string.hybrid_reader_action_listen),
      tint = accent,
      textColor = textColor,
      onClick = onListenClick,
      modifier = Modifier.weight(1f)
    )
    VerticalHairlineDivider(height = 24.dp, color = dividerColor)
    AyahActionCell(
      icon = ReaderIcons.Tafsir,
      label = stringResource(R.string.hybrid_reader_action_tafsir),
      tint = textColor,
      textColor = textColor,
      onClick = onTafsirClick,
      modifier = Modifier.weight(1f)
    )
    VerticalHairlineDivider(height = 24.dp, color = dividerColor)
    AyahActionCell(
      icon = if (isBookmarked) ReaderIcons.BookmarkFilled else ReaderIcons.Bookmark,
      label = stringResource(R.string.hybrid_reader_action_bookmark),
      tint = editorialAccent,
      textColor = if (isBookmarked) editorialAccent else textColor,
      onClick = onBookmarkClick,
      modifier = Modifier.weight(1f)
    )
    VerticalHairlineDivider(height = 24.dp, color = dividerColor)
    AyahActionCell(
      icon = ReaderIcons.Copy,
      label = stringResource(R.string.hybrid_reader_action_copy),
      tint = textColor,
      textColor = textColor,
      onClick = onCopyClick,
      modifier = Modifier.weight(1f)
    )
    VerticalHairlineDivider(height = 24.dp, color = dividerColor)
    AyahActionCell(
      icon = ReaderIcons.Share,
      label = stringResource(R.string.hybrid_reader_action_share),
      tint = textColor,
      textColor = textColor,
      onClick = onShareClick,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun AyahActionCell(
  icon: ImageVector,
  label: String,
  tint: Color,
  textColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .semantics(mergeDescendants = true) {}
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size(15.dp)
    )
    Text(
      text = label,
      color = textColor,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      lineHeight = 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun SelectionTagPill(tagText: String) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val floatBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.88f)
  val shape = RoundedCornerShape(999.dp)
  Text(
    text = tagText,
    color = quranColors.editorialAccent,
    fontSize = 10.5.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = shape,
        ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.16f else 0.06f),
        spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.24f else 0.10f)
      )
      .clip(shape)
      .background(quranColors.glassFloatBg.copy(alpha = 1f))
      .border(width = 1.dp, color = floatBorder, shape = shape)
      .padding(vertical = 3.dp, horizontal = 11.dp)
  )
}

@Composable
private fun AyahCaret(
  pointingUp: Boolean,
  fillColor: androidx.compose.ui.graphics.Color,
  strokeColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier.size(width = 12.dp, height = 7.dp)) {
    val w = size.width
    val h = size.height
    val fillPath = Path().apply {
      if (pointingUp) {
        moveTo(0f, h)
        lineTo(w * 0.5f, 0.5f)
        lineTo(w, h)
        close()
      } else {
        moveTo(0f, 0f)
        lineTo(w * 0.5f, h - 0.5f)
        lineTo(w, 0f)
        close()
      }
    }
    val strokePath = Path().apply {
      if (pointingUp) {
        moveTo(0.5f, h)
        lineTo(w * 0.5f, 1f)
        lineTo(w - 0.5f, h)
      } else {
        moveTo(0.5f, 0f)
        lineTo(w * 0.5f, h - 1f)
        lineTo(w - 0.5f, 0f)
      }
    }
    drawPath(path = fillPath, color = fillColor)
    drawPath(path = strokePath, color = strokeColor, style = Stroke(width = 1.dp.toPx()))
  }
}
