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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.util.QuranUtils

@Composable
internal fun HybridIndexTabContent(
  dailyWird: DailyWirdState,
  fallbackSuraName: String,
  selectedSegment: HubIndexSegment,
  suras: List<SuraIndexItem>,
  juzs: List<JuzIndexItem>,
  hizbs: List<HizbIndexItem>,
  bottomContentPadding: Dp,
  onContinueClick: () -> Unit,
  onEditGoalClick: () -> Unit,
  onSegmentSelected: (HubIndexSegment) -> Unit,
  onPageSelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("hub_index_list"),
    contentPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = 4.dp,
      bottom = bottomContentPadding
    ),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    item(key = "hero_card", contentType = "hero") {
      DailyWirdHeroCard(
        dailyWird = dailyWird,
        fallbackSuraName = fallbackSuraName,
        onContinueClick = onContinueClick,
        onEditGoalClick = onEditGoalClick,
        modifier = Modifier.padding(bottom = 3.dp)
      )
    }

    item(key = "segmented_rail", contentType = "segmented_rail") {
      SegmentedIndexRail(
        selectedSegment = selectedSegment,
        onSegmentSelected = onSegmentSelected,
        modifier = Modifier.padding(bottom = 3.dp)
      )
    }

    when (selectedSegment) {
      HubIndexSegment.SURAS -> {
        items(
          items = suras,
          key = { "sura_${it.sura}" },
          contentType = { "sura_card" }
        ) { sura ->
          SurahIndexCard(
            item = sura,
            onClick = { onPageSelected(sura.startPage) }
          )
        }
      }

      HubIndexSegment.JUZ -> {
        items(
          items = juzs,
          key = { "juz_${it.juz}" },
          contentType = { "juz_card" }
        ) { juz ->
          JuzIndexCard(
            item = juz,
            onClick = { onPageSelected(juz.startPage) }
          )
        }
      }

      HubIndexSegment.HIZB -> {
        items(
          items = hizbs,
          key = { "hizb_${it.hizb}" },
          contentType = { "hizb_card" }
        ) { hizb ->
          HizbIndexCard(
            item = hizb,
            onClick = { onPageSelected(hizb.startPage) }
          )
        }
      }
    }
  }
}

@Composable
private fun SegmentedIndexRail(
  selectedSegment: HubIndexSegment,
  onSegmentSelected: (HubIndexSegment) -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val railShape = CircleShape
  val itemShape = CircleShape
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val railBg = if (isDark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant
  val activeBg = quranColors.surfaceElevated

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(railShape)
      .background(railBg)
      .border(width = 1.dp, color = cardBorder, shape = railShape)
      .padding(4.dp)
      .testTag("hub_segmented_rail"),
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    HubIndexSegment.entries.forEach { segment ->
      val isSelected = segment == selectedSegment
      val label = when (segment) {
        HubIndexSegment.SURAS -> stringResource(R.string.hybrid_hub_tab_suras)
        HubIndexSegment.JUZ -> stringResource(R.string.hybrid_hub_tab_juz)
        HubIndexSegment.HIZB -> stringResource(R.string.hybrid_hub_tab_hizb)
      }
      Box(
        modifier = Modifier
          .weight(1f)
          .then(
            if (isSelected) {
              Modifier
                .shadow(
                  elevation = if (isDark) 7.dp else 6.dp,
                  shape = itemShape,
                  ambientColor = quranColors.shadowTint,
                  spotColor = quranColors.shadowTint
                )
                .clip(itemShape)
                .background(activeBg)
                .border(width = 1.dp, color = cardBorder, shape = itemShape)
            } else {
              Modifier.clip(itemShape)
            }
          )
          .semantics(mergeDescendants = true) { selected = isSelected }
          .clickable(role = Role.Tab) { onSegmentSelected(segment) }
          .padding(vertical = 6.dp)
          .testTag("hub_segment_${segment.name.lowercase()}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = label,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun SurahIndexCard(
  item: SuraIndexItem,
  onClick: () -> Unit
) {
  HubMedallionCard(
    medallionNumber = item.sura,
    title = item.name,
    page = item.startPage,
    onClick = onClick,
    metaContent = {
      Text(
        text = item.metaText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1
      )
      Spacer(modifier = Modifier.width(6.dp))
      VerticalHairlineDivider(
        height = 10.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = stringResource(
          R.string.hybrid_hub_juz_format,
          QuranUtils.getLocalizedNumber(item.juz)
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1
      )
    }
  )
}

@Composable
private fun JuzIndexCard(
  item: JuzIndexItem,
  onClick: () -> Unit
) {
  val localizedAyah = QuranUtils.getLocalizedNumber(item.startAyah)
  HubMedallionCard(
    medallionNumber = item.juz,
    title = item.title,
    page = item.startPage,
    onClick = onClick,
    metaContent = {
      Text(
        text = stringResource(
          R.string.hybrid_hub_ayah_ref_format,
          item.startSuraName,
          localizedAyah
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1
      )
      if (item.previewText.isNotEmpty()) {
        Spacer(modifier = Modifier.width(6.dp))
        VerticalHairlineDivider(
          height = 10.dp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = item.previewText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
      }
    }
  )
}

@Composable
private fun HizbIndexCard(
  item: HizbIndexItem,
  onClick: () -> Unit
) {
  val localizedHizb = QuranUtils.getLocalizedNumber(item.hizb)
  val localizedJuz = QuranUtils.getLocalizedNumber(item.juz)
  val localizedAyah = QuranUtils.getLocalizedNumber(item.startAyah)
  HubMedallionCard(
    medallionNumber = item.hizb,
    title = stringResource(R.string.hybrid_hub_hizb_format, localizedHizb),
    page = item.startPage,
    onClick = onClick,
    metaContent = {
      Text(
        text = stringResource(R.string.hybrid_hub_juz_format, localizedJuz),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1
      )
      Spacer(modifier = Modifier.width(6.dp))
      VerticalHairlineDivider(
        height = 10.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = stringResource(
          R.string.hybrid_hub_ayah_ref_format,
          item.startSuraName,
          localizedAyah
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (item.previewText.isNotEmpty()) {
        Spacer(modifier = Modifier.width(6.dp))
        VerticalHairlineDivider(
          height = 10.dp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = item.previewText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
      }
    }
  )
}

@Composable
internal fun HubMedallionCard(
  medallionNumber: Int,
  title: String,
  page: Int,
  onClick: () -> Unit,
  metaContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(17.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val headingColor = if (isDark) quranColors.onToolbar else MaterialTheme.colorScheme.onBackground
  val pagePillBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.11f)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 13.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
      EditorialRubElHizbMedallion(
        number = medallionNumber,
        size = 38.dp
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp)
      ) {
        Text(
          text = title,
          color = headingColor,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 20.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          content = metaContent
        )
      }
    }

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
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun EditorialRubElHizbMedallion(
  number: Int,
  size: Dp = 38.dp,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val editorialColor = quranColors.editorialAccent
  val fillAlpha = if (isDark) 0.15f else 0.13f

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val squareSide = this.size.minDimension * (26f / 40f)
          val topLeftOffset = Offset(
            x = (this.size.width - squareSide) / 2f,
            y = (this.size.height - squareSide) / 2f
          )
          val squareSize = Size(squareSide, squareSide)
          val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
          val fillColor = editorialColor.copy(alpha = fillAlpha)
          val stroke = Stroke(width = 1.35.dp.toPx())
          val pivot = Offset(this.size.width * 0.5f, this.size.height * 0.5f)

          onDrawBehind {
            drawRoundRect(
              color = fillColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner
            )
            drawRoundRect(
              color = editorialColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner,
              style = stroke
            )
            rotate(degrees = 45f, pivot = pivot) {
              drawRoundRect(
                color = editorialColor,
                topLeft = topLeftOffset,
                size = squareSize,
                cornerRadius = corner,
                style = stroke
              )
            }
          }
        }
    )
    Text(
      text = QuranUtils.getLocalizedNumber(number),
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = editorialColor,
      maxLines = 1
    )
  }
}
