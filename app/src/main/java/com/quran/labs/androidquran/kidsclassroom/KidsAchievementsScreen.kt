package com.quran.labs.androidquran.kidsclassroom

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.MemorizationFormatting

private enum class AchievementsSectionTab {
  SURAH_JOURNEY,
  SURAH_BADGES,
  MILESTONES
}

private enum class SurahTierFilter {
  ALL,
  DIAMOND,
  GOLD,
  SILVER
}

/**
 * Dedicated **Profile & Achievements** screen showcasing the child's Quran learner rank,
 * editable display name, accuracy & streak summary, earned full-Surah high-accuracy badges
 * (`Silver 80%+`, `Gold 90%+`, `Diamond 97%+`), and milestone progress tracker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KidsAchievementsScreen(
  onBack: () -> Unit,
  onPracticeSurah: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val isArabic = remember(context, config) { MemoryFlashcardManager.isAppInArabicMode(context) }
  val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  LaunchedEffect(Unit) {
    RewardsDataStore.initialize(context)
  }

  val rewards by RewardsDataStore.state.collectAsState()
  var activeTab by remember { mutableStateOf(AchievementsSectionTab.SURAH_JOURNEY) }
  var journeyScope by remember { mutableStateOf(SurahJourneyScope.JUZ_AMMA) }
  var selectedJuzDrillDown by remember { mutableStateOf<Int?>(null) }
  var tierFilter by remember { mutableStateOf(SurahTierFilter.ALL) }
  var showEditNameDialog by remember { mutableStateOf(false) }

  BackHandler(enabled = activeTab == AchievementsSectionTab.SURAH_JOURNEY && selectedJuzDrillDown != null) {
    selectedJuzDrillDown = null
  }

  val earnedSurahBadges = remember(rewards) { rewards.earnedSurahBadgesList }
  val filteredSurahBadges = remember(earnedSurahBadges, tierFilter) {
    when (tierFilter) {
      SurahTierFilter.ALL -> earnedSurahBadges
      SurahTierFilter.DIAMOND -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.DIAMOND }
      SurahTierFilter.GOLD -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.GOLD }
      SurahTierFilter.SILVER -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.SILVER }
    }
  }

  val displayName = rewards.learnerName.ifBlank {
    res.getString(R.string.kids_achievements_default_name)
  }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Scaffold(
      modifier = modifier
        .fillMaxSize()
        .testTag("kids_achievements_screen"),
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = res.getString(R.string.kids_achievements_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = res.getString(R.string.kids_achievements_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          },
          navigationIcon = {
            val backCd = res.getString(R.string.kids_achievements_back_cd)
            IconButton(
              onClick = onBack,
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = backCd }
                .testTag("kids_achievements_back_btn")
            ) {
              Text(
                text = if (isArabic) "→" else "←",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .navigationBarsPadding(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // 1. Illuminated Learner Profile Hero Card
          item(key = "profile_hero") {
            LearnerProfileHeroCard(
              displayName = displayName,
              rewards = rewards,
              isArabic = isArabic,
              res = res,
              onEditNameClick = { showEditNameDialog = true }
            )
          }

          // 2. Section Switcher: Surah Journey, Surah Badges, and Milestones
          item(key = "section_tabs") {
            val badgesCountStr = MemoryFlashcardManager.formatNumber(earnedSurahBadges.size, isArabic)
            val unlockedMilestonesStr = MemoryFlashcardManager.formatNumber(rewards.unlockedBadges.size, isArabic)
            SingleChoiceSegmentedButtonRow(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("kids_achievements_tabs")
            ) {
              SegmentedButton(
                selected = activeTab == AchievementsSectionTab.SURAH_JOURNEY,
                onClick = { activeTab = AchievementsSectionTab.SURAH_JOURNEY },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("tab_surah_journey")
              ) {
                Text(
                  text = res.getString(R.string.kids_achievements_tab_journey),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              SegmentedButton(
                selected = activeTab == AchievementsSectionTab.SURAH_BADGES,
                onClick = { activeTab = AchievementsSectionTab.SURAH_BADGES },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("tab_surah_badges")
              ) {
                Text(
                  text = "${res.getString(R.string.kids_achievements_tab_surah_badges)} ($badgesCountStr)",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              SegmentedButton(
                selected = activeTab == AchievementsSectionTab.MILESTONES,
                onClick = { activeTab = AchievementsSectionTab.MILESTONES },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("tab_milestones")
              ) {
                Text(
                  text = "${res.getString(R.string.kids_achievements_tab_milestones)} ($unlockedMilestonesStr)",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          when (activeTab) {
            AchievementsSectionTab.SURAH_JOURNEY -> {
              surahJourneyMapItems(
                scope = journeyScope,
                selectedJuzForDrillDown = selectedJuzDrillDown,
                rewards = rewards,
                isArabic = isArabic,
                res = res,
                onSelectScope = { newScope ->
                  journeyScope = newScope
                  selectedJuzDrillDown = null
                },
                onOpenJuzDrillDown = { juzNum ->
                  selectedJuzDrillDown = juzNum
                },
                onBackToAll30Juz = {
                  selectedJuzDrillDown = null
                },
                onPracticeSurah = onPracticeSurah
              )
            }

            AchievementsSectionTab.SURAH_BADGES -> {
            // Tier filter chips
            item(key = "tier_filters") {
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("surah_badge_tier_filters")
              ) {
                val filterItems = listOf(
                  Triple(SurahTierFilter.ALL, res.getString(R.string.kids_achievements_filter_all), earnedSurahBadges.size),
                  Triple(SurahTierFilter.DIAMOND, "💎 ${res.getString(R.string.kids_achievements_filter_diamond)}", rewards.diamondBadgeCount),
                  Triple(SurahTierFilter.GOLD, "🥇 ${res.getString(R.string.kids_achievements_filter_gold)}", rewards.goldBadgeCount),
                  Triple(SurahTierFilter.SILVER, "🥈 ${res.getString(R.string.kids_achievements_filter_silver)}", rewards.silverBadgeCount)
                )
                items(filterItems, key = { it.first.name }) { (filter, label, count) ->
                  val countText = MemoryFlashcardManager.formatNumber(count, isArabic)
                  FilterChip(
                    selected = tierFilter == filter,
                    onClick = { tierFilter = filter },
                    label = {
                      Text(
                        text = "$label ($countText)",
                        fontWeight = FontWeight.SemiBold
                      )
                    },
                    modifier = Modifier
                      .defaultMinSize(minHeight = 48.dp)
                      .testTag("tier_filter_${filter.name.lowercase()}")
                  )
                }
              }
            }

            if (filteredSurahBadges.isEmpty()) {
              item(key = "empty_surah_badges") {
                EmptySurahBadgesCard(
                  hasAnyBadges = earnedSurahBadges.isNotEmpty(),
                  isArabic = isArabic,
                  res = res,
                  onPracticeSurah = onPracticeSurah
                )
              }
            } else {
              // Responsive 2-column rows for crisp badge trophy showcase
              val rows = filteredSurahBadges.chunked(2)
              items(rows, key = { row -> row.joinToString("_") { it.surah.toString() } }) { rowBadges ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  for (badge in rowBadges) {
                    SurahBadgeTrophyCard(
                      badge = badge,
                      isArabic = isArabic,
                      res = res,
                      onPracticeSurah = { onPracticeSurah(badge.surah) },
                      modifier = Modifier.weight(1f)
                    )
                  }
                  if (rowBadges.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
            }

            AchievementsSectionTab.MILESTONES -> {
              // Milestones Catalog (Unlocked first, then In-Progress)
              val orderedMilestones = rewards.milestoneBadges.sortedByDescending { it.isUnlocked }
              items(orderedMilestones, key = { it.id }) { badge ->
                MilestoneBadgeCard(
                  badge = badge,
                  isArabic = isArabic,
                  res = res
                )
              }
            }
          }

          item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }

    if (showEditNameDialog) {
      EditLearnerNameDialog(
        currentName = rewards.learnerName,
        res = res,
        onDismiss = { showEditNameDialog = false },
        onSave = { newName ->
          RewardsDataStore.setLearnerName(context, newName)
          showEditNameDialog = false
        }
      )
    }
  }
}

@Composable
internal fun LearnerProfileHeroCard(
  displayName: String,
  rewards: KidsRewardsState,
  isArabic: Boolean,
  res: android.content.res.Resources,
  onEditNameClick: () -> Unit
) {
  val rank = rewards.learnerRank
  val nextRank = rank.nextRank()
  val badgeCount = rewards.earnedSurahBadgesList.size

  val rankProgress = if (nextRank == null) {
    1f
  } else {
    val span = (nextRank.minSurahBadges - rank.minSurahBadges).coerceAtLeast(1)
    ((badgeCount - rank.minSurahBadges).toFloat() / span.toFloat()).coerceIn(0f, 1f)
  }

  Surface(
    shape = RoundedCornerShape(28.dp),
    shadowElevation = 6.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("learner_profile_hero_card")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF064E3B),
              Color(0xFF0F766E),
              Color(0xFF134E4A)
            )
          )
        )
    ) {
      // Decorative illuminated star-mesh backdrop
      Canvas(modifier = Modifier.matchParentSize()) {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFDE047).copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * 0.88f, size.height * 0.18f),
            radius = size.width * 0.45f
          ),
          radius = size.width * 0.45f,
          center = Offset(size.width * 0.88f, size.height * 0.18f)
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF34D399).copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.85f),
            radius = size.width * 0.35f
          ),
          radius = size.width * 0.35f,
          center = Offset(size.width * 0.12f, size.height * 0.85f)
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Profile Avatar + Name + Rank Pill
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(Color(0xFFFFD166).copy(alpha = 0.22f))
          ) {
            Text(
              text = rank.emoji,
              fontSize = 32.sp
            )
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .weight(1f, fill = false)
                  .testTag("learner_profile_name")
              )
              val editNameCd = res.getString(R.string.kids_achievements_edit_name_cd)
              IconButton(
                onClick = onEditNameClick,
                modifier = Modifier
                  .size(48.dp)
                  .semantics { contentDescription = editNameCd }
                  .testTag("edit_learner_name_btn")
              ) {
                Text(
                  text = "✏️",
                  fontSize = 16.sp
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color(0xFFFFD166).copy(alpha = 0.22f),
              border = BorderStroke(1.dp, Color(0xFFFFD166).copy(alpha = 0.55f))
            ) {
              Text(
                text = "${rank.emoji} ${if (isArabic) rank.titleAr else rank.titleEn}",
                color = Color(0xFFFEF08A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .padding(horizontal = 10.dp, vertical = 4.dp)
                  .testTag("learner_rank_badge")
              )
            }
          }
        }

        // Next Rank Progress Bar
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val rankProgressLabel = if (nextRank == null) {
            res.getString(R.string.kids_achievements_rank_max)
          } else {
            val nextTitle = if (isArabic) nextRank.titleAr else nextRank.titleEn
            res.getString(
              R.string.kids_achievements_rank_next,
              nextTitle,
              MemoryFlashcardManager.formatNumber(badgeCount, isArabic),
              MemoryFlashcardManager.formatNumber(nextRank.minSurahBadges, isArabic)
            )
          }
          Text(
            text = rankProgressLabel,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
          )
          LinearProgressIndicator(
            progress = { rankProgress },
            color = Color(0xFFFACC15),
            trackColor = Color.White.copy(alpha = 0.18f),
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(999.dp))
          )
        }

        // 4-Stat Metric Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ProfileStatTile(
            emoji = "🏅",
            value = MemoryFlashcardManager.formatNumber(badgeCount, isArabic),
            label = res.getString(R.string.kids_achievements_stat_badges),
            modifier = Modifier.weight(1f)
          )
          ProfileStatTile(
            emoji = "⭐",
            value = MemoryFlashcardManager.formatNumber(rewards.totalStars, isArabic),
            label = res.getString(R.string.kids_achievements_stat_stars),
            modifier = Modifier.weight(1f)
          )
          ProfileStatTile(
            emoji = "🔥",
            value = MemoryFlashcardManager.formatNumber(rewards.streakDays, isArabic),
            label = res.getString(R.string.kids_achievements_stat_streak),
            modifier = Modifier.weight(1f)
          )
          val avgAccValue = if (badgeCount > 0) {
            "${MemoryFlashcardManager.formatNumber(rewards.averageSurahBadgeAccuracy, isArabic)}%"
          } else {
            "—"
          }
          ProfileStatTile(
            emoji = "🎯",
            value = avgAccValue,
            label = res.getString(R.string.kids_achievements_stat_accuracy),
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun ProfileStatTile(
  emoji: String,
  value: String,
  label: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.White.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(text = emoji, fontSize = 16.sp)
      Text(
        text = value,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1
      )
      Text(
        text = label,
        color = Color.White.copy(alpha = 0.80f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
internal fun SurahBadgeTrophyCard(
  badge: SurahCompletionBadge,
  isArabic: Boolean,
  res: android.content.res.Resources,
  onPracticeSurah: () -> Unit,
  modifier: Modifier = Modifier
) {
  val style = remember(badge.tier) { visualStyleForTier(badge.tier) }
  val surahNumStr = MemoryFlashcardManager.formatNumber(badge.surah, isArabic)
  val accStr = MemoryFlashcardManager.formatNumber(badge.bestAccuracyPercent, isArabic)
  val countStr = MemoryFlashcardManager.formatNumber(badge.completionsCount, isArabic)
  val tierLabel = if (isArabic) badge.tier.titleAr else badge.tier.titleEn

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.5.dp, style.cardAccent.copy(alpha = 0.48f)),
    tonalElevation = 2.dp,
    modifier = modifier.testTag("surah_badge_card_${badge.surah}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Tier pill
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = style.cardAccent.copy(alpha = 0.16f)
      ) {
        Text(
          text = "${badge.tier.emoji} $tierLabel",
          color = style.cardAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
      }

      // Custom Geometric Medallion
      SurahBadgeMedallion(
        tier = badge.tier,
        centerLabel = "$accStr%",
        size = 78.dp,
        animateGlow = badge.tier == SurahBadgeTier.DIAMOND
      )

      // Surah Name & Number
      Text(
        text = badge.surahNameAr,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "#$surahNumStr · ${res.getString(R.string.kids_achievements_badge_completions_format, countStr)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      // 1-tap Practice / Upgrade Tier button
      FilledTonalButton(
        onClick = onPracticeSurah,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 48.dp)
          .testTag("practice_surah_badge_btn_${badge.surah}")
      ) {
        Text(
          text = "▶",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (badge.tier == SurahBadgeTier.DIAMOND) {
            res.getString(R.string.kids_achievements_practice_surah_btn)
          } else {
            res.getString(R.string.kids_achievements_upgrade_badge_btn)
          },
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptySurahBadgesCard(
  hasAnyBadges: Boolean,
  isArabic: Boolean,
  res: android.content.res.Resources,
  onPracticeSurah: (Int) -> Unit
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("empty_surah_badges_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SurahBadgeMedallion(tier = SurahBadgeTier.SILVER, centerLabel = "80%", size = 56.dp)
        SurahBadgeMedallion(tier = SurahBadgeTier.GOLD, centerLabel = "90%", size = 66.dp)
        SurahBadgeMedallion(tier = SurahBadgeTier.DIAMOND, centerLabel = "97%", size = 56.dp)
      }

      Text(
        text = res.getString(
          if (hasAnyBadges) R.string.kids_achievements_empty_filter_title
          else R.string.kids_achievements_empty_title
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
      )
      Text(
        text = res.getString(
          if (hasAnyBadges) R.string.kids_achievements_empty_filter_subtitle
          else R.string.kids_achievements_empty_subtitle
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      if (!hasAnyBadges) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = res.getString(R.string.kids_achievements_quick_start_label),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
        val starterSurahs = listOf(1, 112, 113, 114)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          starterSurahs.forEach { surahNum ->
            val surahName = MinshawiJuz30Repository.getSurahNameAr(surahNum)
            val numStr = MemoryFlashcardManager.formatNumber(surahNum, isArabic)
            OutlinedButton(
              onClick = { onPracticeSurah(surahNum) },
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("quick_start_surah_$surahNum")
            ) {
              Text(
                text = "$numStr · $surahName",
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun MilestoneBadgeCard(
  badge: KidsBadge,
  isArabic: Boolean,
  res: android.content.res.Resources
) {
  val title = if (isArabic) badge.titleAr else badge.titleEn
  val desc = if (isArabic) badge.descriptionAr else badge.descriptionEn
  val curStr = MemoryFlashcardManager.formatNumber(badge.progressCurrent, isArabic)
  val tgtStr = MemoryFlashcardManager.formatNumber(badge.progressTarget, isArabic)

  val containerColor = if (badge.isUnlocked) {
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerLow
  }
  val borderColor = if (badge.isUnlocked) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
  } else {
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
  }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("milestone_badge_${badge.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(54.dp)
          .clip(CircleShape)
          .background(
            if (badge.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceContainerHighest
          )
      ) {
        Text(
          text = badge.emoji,
          fontSize = 26.sp
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (badge.isUnlocked) Color(0xFF059669).copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainerHighest
          ) {
            Text(
              text = if (badge.isUnlocked) {
                "✓ ${res.getString(R.string.kids_achievements_unlocked_label)}"
              } else {
                "🔒 ${res.getString(R.string.kids_achievements_locked_label)}"
              },
              color = if (badge.isUnlocked) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }

        if (desc.isNotBlank()) {
          Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          LinearProgressIndicator(
            progress = { badge.progressFraction },
            color = if (badge.isUnlocked) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
              .weight(1f)
              .height(7.dp)
              .clip(RoundedCornerShape(999.dp))
          )
          Text(
            text = "$curStr / $tgtStr",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
internal fun EditLearnerNameDialog(
  currentName: String,
  res: android.content.res.Resources,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit
) {
  var draft by remember(currentName) { mutableStateOf(currentName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = res.getString(R.string.kids_achievements_edit_name_title),
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= 28) draft = it },
        singleLine = true,
        placeholder = { Text(res.getString(R.string.kids_achievements_edit_name_hint)) },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("edit_learner_name_input")
      )
    },
    confirmButton = {
      Button(
        onClick = { onSave(draft) },
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("save_learner_name_btn")
      ) {
        Text(res.getString(R.string.kids_achievements_save_btn))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
      ) {
        Text(res.getString(R.string.kids_achievements_cancel_btn))
      }
    }
  )
}
