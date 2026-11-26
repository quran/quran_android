package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.util.QuranUtils

@Composable
internal fun HybridBookmarksTabContent(
  state: HubBookmarksUiState,
  bottomContentPadding: Dp,
  onOpenPage: (Int) -> Unit,
  onOpenAyah: (page: Int, sura: Int, ayah: Int) -> Unit,
  onClearReadingBookmark: (ReadingBookmarkType) -> Unit,
  onRemoveRecentPage: (Int) -> Unit,
  onClearAllRecents: () -> Unit,
  onRemoveAyahBookmark: (sura: Int, ayah: Int) -> Unit,
  onClearHighlight: (sura: Int, ayah: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground

  if (state.isLoaded && state.isEmpty) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
        .padding(bottom = bottomContentPadding)
        .testTag("hub_bookmarks_empty"),
      contentAlignment = Alignment.Center
    ) {
      val emptyBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(quranColors.cardSurface)
          .border(1.dp, emptyBorder, RoundedCornerShape(22.dp))
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
              quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = BookmarkArchitecturalIcon,
            contentDescription = null,
            tint = quranColors.editorialAccent,
            modifier = Modifier.size(22.dp)
          )
        }
        Text(
          text = stringResource(R.string.hybrid_hub_saved_empty_title),
          color = headingColor,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
        Text(
          text = stringResource(R.string.hybrid_hub_saved_empty_subtitle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          lineHeight = 19.sp,
          textAlign = TextAlign.Center
        )
      }
    }
    return
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("hub_bookmarks_list"),
    contentPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = 4.dp,
      bottom = bottomContentPadding
    ),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (state.readingBookmarks.isNotEmpty()) {
      item(key = "header_reading", contentType = "section_header") {
        SavedSectionHeader(title = stringResource(R.string.hybrid_hub_saved_reading))
      }
      items(
        items = state.readingBookmarks,
        key = { "reading_${it.slot.name}" },
        contentType = { "saved_card" }
      ) { item ->
        val subtitle = if (item.sura != null && item.ayah != null) {
          stringResource(
            R.string.hybrid_hub_ayah_ref_format,
            item.suraName,
            QuranUtils.getLocalizedNumber(item.ayah)
          )
        } else {
          item.suraName
        }
        val slotColor = colorResource(ReadingBookmarkSlots[item.slot].colorResourceId)
        val slotBadgeBg = slotColor.copy(alpha = if (isDark) 0.18f else 0.14f)
        val slotLabel = ReadingBookmarkSlots.displayName(item.slot, item.customName)
        SavedEntryCard(
          title = subtitle,
          juz = item.juz,
          page = item.page,
          snippet = null,
          iconTint = slotColor,
          iconBadgeBg = slotBadgeBg,
          useFilledIcon = true,
          slotLabel = slotLabel,
          onClick = {
            if (item.sura != null && item.ayah != null) {
              onOpenAyah(item.page, item.sura, item.ayah)
            } else {
              onOpenPage(item.page)
            }
          },
          onRemove = { onClearReadingBookmark(item.slot) }
        )
      }
    }

    if (state.recentPages.isNotEmpty()) {
      item(key = "header_recents", contentType = "section_header_recents") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          SavedSectionHeader(
            title = stringResource(R.string.hybrid_hub_saved_recents),
            modifier = Modifier.weight(1f)
          )
          Text(
            text = stringResource(R.string.hybrid_hub_saved_clear_recents),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clip(CircleShape)
              .clickable(role = Role.Button, onClick = onClearAllRecents)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
      items(
        items = state.recentPages,
        key = { "recent_${it.page}" },
        contentType = { "saved_card" }
      ) { item ->
        SavedEntryCard(
          title = item.suraName,
          juz = item.juz,
          page = item.page,
          snippet = null,
          iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
          iconBadgeBg = MaterialTheme.colorScheme.surfaceVariant,
          useFilledIcon = false,
          onClick = { onOpenPage(item.page) },
          onRemove = { onRemoveRecentPage(item.page) }
        )
      }
    }

    if (state.ayahBookmarks.isNotEmpty()) {
      item(key = "header_ayahs", contentType = "section_header") {
        SavedSectionHeader(
          title = stringResource(R.string.hybrid_hub_saved_ayahs),
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      items(
        items = state.ayahBookmarks,
        key = { "bookmark_${it.id}_${it.sura}_${it.ayah}" },
        contentType = { "saved_card_snippet" }
      ) { item ->
        val editorialSoftBg = quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)
        SavedEntryCard(
          title = stringResource(
            R.string.hybrid_hub_ayah_ref_format,
            item.suraName,
            QuranUtils.getLocalizedNumber(item.ayah)
          ),
          juz = item.juz,
          page = item.page,
          snippet = item.ayahText,
          iconTint = quranColors.editorialAccent,
          iconBadgeBg = editorialSoftBg,
          useFilledIcon = true,
          onClick = { onOpenAyah(item.page, item.sura, item.ayah) },
          onRemove = { onRemoveAyahBookmark(item.sura, item.ayah) }
        )
      }
    }

    if (state.highlights.isNotEmpty()) {
      item(key = "header_highlights", contentType = "section_header") {
        SavedSectionHeader(
          title = stringResource(R.string.hybrid_hub_saved_highlights),
          modifier = Modifier.padding(top = 4.dp)
        )
      }
      items(
        items = state.highlights,
        key = { "highlight_${it.sura}_${it.ayah}" },
        contentType = { "saved_card_snippet" }
      ) { item ->
        val highlightTint = item.color?.let { colorResource(highlightListColorRes(it)) }
          ?: quranColors.editorialAccent
        val highlightBadgeBg = if (item.color != null) {
          highlightTint.copy(alpha = if (isDark) 0.18f else 0.14f)
        } else {
          quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)
        }
        SavedEntryCard(
          title = stringResource(
            R.string.hybrid_hub_ayah_ref_format,
            item.suraName,
            QuranUtils.getLocalizedNumber(item.ayah)
          ),
          juz = item.juz,
          page = item.page,
          snippet = item.ayahText,
          iconTint = highlightTint,
          iconBadgeBg = highlightBadgeBg,
          useFilledIcon = true,
          onClick = { onOpenAyah(item.page, item.sura, item.ayah) },
          onRemove = { onClearHighlight(item.sura, item.ayah) }
        )
      }
    }
  }
}

@Composable
private fun SavedSectionHeader(
  title: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = title,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
  )
}

@Composable
private fun SavedEntryCard(
  title: String,
  juz: Int,
  page: Int,
  snippet: String?,
  iconTint: Color,
  iconBadgeBg: Color,
  useFilledIcon: Boolean = true,
  slotLabel: String? = null,
  onClick: () -> Unit,
  onRemove: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(17.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val pagePillBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.11f)
  val closePillBg = if (isDark) quranColors.surfaceElevated else MaterialTheme.colorScheme.surfaceVariant

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(1.dp, cardBorder, cardShape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 13.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(iconBadgeBg),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (useFilledIcon) {
              BookmarkFilledArchitecturalIcon
            } else {
              BookmarkArchitecturalIcon
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = title,
            color = headingColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!slotLabel.isNullOrBlank()) {
              Text(
                text = slotLabel,
                color = iconTint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.width(6.dp))
              VerticalHairlineDivider(
                height = 10.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
              )
              Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
              text = stringResource(
                R.string.hybrid_hub_juz_format,
                QuranUtils.getLocalizedNumber(juz)
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            VerticalHairlineDivider(
              height = 10.dp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(
                R.string.hybrid_hub_page_full_format,
                QuranUtils.getLocalizedNumber(page)
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(pagePillBg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
          Text(
            text = stringResource(
              R.string.hybrid_hub_page_pill_format,
              QuranUtils.getLocalizedNumber(page)
            ),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(closePillBg)
            .clickable(role = Role.Button, onClick = onRemove),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = CloseArchitecturalIcon,
            contentDescription = stringResource(R.string.hybrid_hub_saved_remove_desc),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }

    if (!snippet.isNullOrBlank()) {
      Text(
        text = snippet,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
