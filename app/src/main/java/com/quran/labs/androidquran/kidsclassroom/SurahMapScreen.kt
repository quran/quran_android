package com.quran.labs.androidquran.kidsclassroom

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R

private enum class SurahMapFilter {
  KIDS_JOURNEY, // Al-Fatiha + Juz 30 (Surahs 114 down to 78)
  ALL_114       // All 114 Surahs (1..114)
}

/**
 * Joyful Surah Adventure Map covering all 114 Surahs (6,236 Ayat) with stars earned,
 * mastered progress bars, unlocked kid badges, search, and one-tap jump to any Surah lesson.
 */
@Composable
fun SurahMapScreen(
  surahs: List<KidsSurahInfo>,
  currentSurahNumber: Int,
  rewards: KidsRewardsState,
  onSelectSurah: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var filter by remember { mutableStateOf(SurahMapFilter.KIDS_JOURNEY) }
  var searchQuery by remember { mutableStateOf("") }

  val displayedSurahs = remember(surahs, filter, searchQuery) {
    val base = when (filter) {
      SurahMapFilter.KIDS_JOURNEY -> {
        val fatiha = surahs.filter { it.surahNumber == 1 }
        val juz30 = surahs.filter { it.surahNumber in 78..114 }.sortedByDescending { it.surahNumber }
        fatiha + juz30
      }
      SurahMapFilter.ALL_114 -> surahs
    }
    val q = searchQuery.trim()
    if (q.isEmpty()) base
    else base.filter {
      it.nameAr.contains(q, ignoreCase = true) ||
        it.surahNumber.toString() == q
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color(0xFFFFFBF2), Color(0xFFF0FDF4), Color(0xFFEFF6FF))
        )
      )
      .navigationBarsPadding()
      .testTag("surah_map_screen")
  ) {
    // Adventure Top Header
    Surface(
      color = Color(0xFF0F766E),
      shadowElevation = 4.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("surah_map_back_btn")
          ) {
            Text(
              text = "←",
              fontSize = 22.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.kids_map_title),
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
            Text(
              text = stringResource(R.string.kids_map_subtitle),
              fontSize = 12.sp,
              color = Color(0xFFCCFBF1)
            )
          }
          // Stars & Streak pills
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color(0xFFFFF8E1)
            ) {
              Text(
                text = "⭐ ${rewards.totalStars}",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFB45309),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
            if (rewards.streakDays > 0) {
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFEDD5)
              ) {
                Text(
                  text = "🔥 ${rewards.streakDays}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFC2410C),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
          }
        }

        if (rewards.unlockedBadges.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            rewards.unlockedBadges.take(4).forEach { badge ->
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.16f)
              ) {
                Text(
                  text = "${badge.emoji} ${badge.titleAr}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }
          }
        }
      }
    }

    // Search + Filter Row
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        leadingIcon = {
          Text(
            text = "🔍",
            fontSize = 16.sp
          )
        },
        placeholder = {
          Text(
            text = stringResource(R.string.kids_map_search_hint),
            fontSize = 13.sp
          )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Color(0xFF0F766E),
          unfocusedBorderColor = Color(0xFFCBD5E1),
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("surah_map_search_input")
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = filter == SurahMapFilter.KIDS_JOURNEY,
          onClick = { filter = SurahMapFilter.KIDS_JOURNEY },
          label = { Text(stringResource(R.string.kids_map_filter_juz30), fontWeight = FontWeight.Bold) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF0F766E),
            selectedLabelColor = Color.White
          ),
          modifier = Modifier.testTag("surah_map_filter_juz30")
        )
        FilterChip(
          selected = filter == SurahMapFilter.ALL_114,
          onClick = { filter = SurahMapFilter.ALL_114 },
          label = { Text(stringResource(R.string.kids_map_filter_all), fontWeight = FontWeight.Bold) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF0F766E),
            selectedLabelColor = Color.White
          ),
          modifier = Modifier.testTag("surah_map_filter_all")
        )
      }
    }

    // Winding Stepping-Stone Surah Adventure Cards
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      itemsIndexed(displayedSurahs, key = { _, item -> item.surahNumber }) { index, surah ->
        val earnedStars = rewards.surahStars[surah.surahNumber] ?: 0
        val maxStars = (surah.ayahCount * 3).coerceAtLeast(3)
        val masteredAyat = rewards.surahMasteredCount[surah.surahNumber] ?: 0
        val progress = (masteredAyat.toFloat() / surah.ayahCount.coerceAtLeast(1).toFloat())
          .coerceIn(0f, 1f)
        val isCurrent = surah.surahNumber == currentSurahNumber

        val nodePalette = listOf(
          Color(0xFF0F766E),
          Color(0xFF0284C7),
          Color(0xFFD97706),
          Color(0xFF7C3AED),
          Color(0xFF059669)
        )
        val badgeColor = nodePalette[index % nodePalette.size]

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = if (isCurrent) Color(0xFFECFDF5) else Color.White,
          shadowElevation = if (isCurrent) 4.dp else 1.5.dp,
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = if (isCurrent) 2.dp else 1.dp,
              color = if (isCurrent) Color(0xFF10B981) else Color(0xFFE2E8F0),
              shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable { onSelectSurah(surah.surahNumber) }
            .testTag("surah_map_item_${surah.surahNumber}")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Circular stepping-stone Surah number medallion
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(badgeColor),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = surah.surahNumber.toString(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
              )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = "سورة ${surah.nameAr}",
                  fontSize = 17.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF0F172A),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (isCurrent) {
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF10B981)
                  ) {
                    Text(
                      text = stringResource(R.string.kids_map_active_badge),
                      color = Color.White,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                  }
                }
              }

              Text(
                text = stringResource(R.string.kids_map_ayah_progress, masteredAyat, surah.ayahCount),
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )

              Spacer(Modifier.height(6.dp))

              LinearProgressIndicator(
                progress = { progress },
                color = badgeColor,
                trackColor = badgeColor.copy(alpha = 0.14f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(999.dp))
              )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (earnedStars > 0) "⭐" else "☆",
                  fontSize = 16.sp,
                  color = if (earnedStars > 0) Color(0xFFF59E0B) else Color(0xFFCBD5E1)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                  text = "$earnedStars/$maxStars",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (earnedStars > 0) Color(0xFFB45309) else Color(0xFF94A3B8)
                )
              }
            }
          }
        }
      }
    }
  }
}
