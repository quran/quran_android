package com.quran.labs.androidquran.kidsclassroom

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.MemorizationFormatting
import kotlinx.coroutines.launch

private val KidsDockEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private enum class KidsRewardsSubTab {
  SURAH_BADGES,
  MILESTONES
}

private enum class KidsRewardsTierFilter {
  ALL,
  DIAMOND,
  GOLD,
  SILVER
}

/**
 * Full-screen dedicated **Kids Mode** universe uniting:
 * - **Progress Islands** (interactive archipelago map & destination when returning from lessons)
 * - **Rewards Vault** (Surah Diamond/Gold/Silver medallions, stars, streak, learner rank, milestones)
 * - **My Space** (editable profile, teacher reciter, difficulty, repeat targets, tricky-word revision,
 *   and a clean switch back to Normal Mode)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsModeRootScreen(
  initialTab: KidsModeTab = KidsModeTab.ISLANDS,
  requestedTabOverride: KidsModeTab? = null,
  onTabOverrideConsumed: () -> Unit = {},
  onExitToNormalMode: () -> Unit,
  onLaunchSurahLesson: (surah: Int, ayah: Int, launchMode: KidsLessonLaunchMode) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val haptics = LocalHapticFeedback.current
  val isDark = isSystemInDarkTheme()
  val isArabic = remember(context, config) { MemoryFlashcardManager.isAppInArabicMode(context) }
  val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    RewardsDataStore.initialize(context)
    KidsSurahAnalysisStore.ensureLoaded(context)
    KidsClassroomManager.ensureKidsTabInitialized(context)
  }

  val rewards by RewardsDataStore.state.collectAsState()
  val kidsState by KidsClassroomManager.uiState.collectAsState()
  val returnEvent by KidsModeNavigationState.pendingReturnEvent.collectAsState()
  val analysisSummaries by KidsSurahAnalysisStore.summaries.collectAsState()

  var activeTab by remember { mutableStateOf(initialTab) }
  var showEditNameDialog by remember { mutableStateOf(false) }
  var analysisSheetSurah by remember { mutableStateOf<Int?>(null) }

  // Honor tab override from onNewIntent (e.g. returning directly to Islands or Rewards)
  LaunchedEffect(requestedTabOverride) {
    if (requestedTabOverride != null) {
      activeTab = requestedTabOverride
      onTabOverrideConsumed()
    }
  }

  // Whenever a lesson returns to Kids Mode, jump straight to Progress Islands
  LaunchedEffect(returnEvent?.eventId) {
    if (returnEvent != null) {
      activeTab = KidsModeTab.ISLANDS
    }
  }

  // Pressing Back on Rewards or My Space returns to Progress Islands first
  BackHandler(enabled = activeTab != KidsModeTab.ISLANDS) {
    activeTab = KidsModeTab.ISLANDS
  }

  val displayName = rewards.learnerName.ifBlank {
    res.getString(R.string.kids_achievements_default_name)
  }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(if (isDark) Color(0xFF071B2A) else Color(0xFFE0F7FA))
        .testTag("kids_mode_root_screen")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Persistent lightweight Kids Explorer Top Bar
        KidsExplorerTopBar(
          displayName = displayName,
          rewards = rewards,
          isArabic = isArabic,
          isDark = isDark,
          res = res,
          onProfileClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            showEditNameDialog = true
          },
          onRewardsPillClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            activeTab = KidsModeTab.REWARDS
          },
          onSwitchToNormalMode = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onExitToNormalMode()
          }
        )

        // Main Tab Content
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
              fadeIn(tween(180, easing = KidsDockEaseOut)) togetherWith
                fadeOut(tween(140, easing = KidsDockEaseOut))
            },
            label = "kidsModeTabSwap"
          ) { tab ->
            when (tab) {
              KidsModeTab.ISLANDS -> {
                KidsProgressIslandsView(
                  rewards = rewards,
                  returnEvent = returnEvent,
                  isArabic = isArabic,
                  res = res,
                  onConsumeReturnEvent = { id -> KidsModeNavigationState.consumeReturnEvent(id) },
                  onLaunchSurah = { surah, mode ->
                    onLaunchSurahLesson(surah, 1, mode)
                  },
                  onOpenSurahAnalysis = { surah ->
                    analysisSheetSurah = surah
                  }
                )
              }

              KidsModeTab.REWARDS -> {
                KidsRewardsVaultTab(
                  displayName = displayName,
                  rewards = rewards,
                  isArabic = isArabic,
                  isDark = isDark,
                  res = res,
                  onEditNameClick = { showEditNameDialog = true },
                  onPracticeSurah = { surah ->
                    onLaunchSurahLesson(surah, 1, KidsLessonLaunchMode.OPEN_READY)
                  }
                )
              }

              KidsModeTab.PRACTICE_HUB -> {
                KidsPracticeHubTab(
                  displayName = displayName,
                  rewards = rewards,
                  kidsState = kidsState,
                  analysisSummaries = analysisSummaries,
                  isArabic = isArabic,
                  isDark = isDark,
                  res = res,
                  onEditNameClick = { showEditNameDialog = true },
                  onSelectQari = { qari -> KidsClassroomManager.setSelectedTeacherQari(context, qari) },
                  onSelectDifficulty = { level -> KidsClassroomManager.setDifficultyLevel(context, level) },
                  onSetAyahRepeat = { count -> KidsClassroomManager.setAyahRepeatTarget(context, count) },
                  onSetSurahRepeat = { count -> KidsClassroomManager.setSurahRepeatTarget(context, count) },
                  onToggleAutoNext = { enabled -> KidsClassroomManager.setAutoNextEnabled(context, enabled) },
                  onToggleRepeatFromMistake = { enabled ->
                    KidsClassroomManager.setRepeatFromFirstMistakeOnly(context, enabled)
                  },
                  onOpenSurahAnalysis = { surah -> analysisSheetSurah = surah },
                  onSwitchToNormalMode = onExitToNormalMode
                )
              }
            }
          }
        }
      }

      // Floating Tactile 3-Tab Kids Dock
      KidsFloatingBottomDock(
        activeTab = activeTab,
        badgeCount = rewards.earnedSurahBadgesList.size,
        isArabic = isArabic,
        isDark = isDark,
        res = res,
        onSelectTab = { newTab ->
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          activeTab = newTab
        },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp)
      )
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

    val targetAnalysisSurah = analysisSheetSurah
    if (targetAnalysisSurah != null) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = { analysisSheetSurah = null },
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.40f)
      ) {
        KidsSurahAnalysisContent(
          initialSurah = targetAnalysisSurah,
          onListenWordSlowMo = { suraAyah, wordPos ->
            KidsClassroomManager.replayWordInSlowMo(context, suraAyah, wordPos)
          },
          onPracticeAyah = { suraAyah: SuraAyah ->
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
              analysisSheetSurah = null
              onLaunchSurahLesson(suraAyah.sura, suraAyah.ayah, KidsLessonLaunchMode.LISTEN_AND_REPEAT)
            }
          },
          onClearSurah = { surah ->
            KidsSurahAnalysisStore.clearSurah(context, surah)
          }
        )
      }
    }
  }
}

@Composable
private fun KidsExplorerTopBar(
  displayName: String,
  rewards: KidsRewardsState,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onProfileClick: () -> Unit,
  onRewardsPillClick: () -> Unit,
  onSwitchToNormalMode: () -> Unit
) {
  val rank = rewards.learnerRank
  val starsStr = MemoryFlashcardManager.formatNumber(rewards.totalStars, isArabic)
  val streakStr = MemoryFlashcardManager.formatNumber(rewards.streakDays, isArabic)

  Surface(
    color = if (isDark) Color(0xFF0A2233) else Color(0xFF0F766E),
    shadowElevation = 4.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_mode_top_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Child Avatar & Name Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onProfileClick)
          .padding(horizontal = 4.dp, vertical = 2.dp)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFFFACC15).copy(alpha = 0.24f))
        ) {
          Text(text = rank.emoji, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = displayName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = res.getString(R.string.kids_mode_topbar_badge),
            color = Color(0xFF99F6E4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
        }
      }

      // Live Stars & Streak Pills
      Surface(
        onClick = onRewardsPillClick,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFFF8E1),
        modifier = Modifier.testTag("kids_topbar_stars_pill")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = "⭐ $starsStr",
            color = Color(0xFFB45309),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
          )
          if (rewards.streakDays > 0) {
            Text(
              text = "🔥 $streakStr",
              color = Color(0xFFC2410C),
              fontSize = 12.sp,
              fontWeight = FontWeight.ExtraBold,
              style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            )
          }
        }
      }

      // Clean Parent-Friendly Exit to Normal Mode
      Surface(
        onClick = onSwitchToNormalMode,
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        modifier = Modifier.testTag("kids_topbar_normal_mode_btn")
      ) {
        Text(
          text = "📖 ${res.getString(R.string.kids_mode_switch_normal_short)}",
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }
  }
}

@Composable
private fun KidsFloatingBottomDock(
  activeTab: KidsModeTab,
  badgeCount: Int,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onSelectTab: (KidsModeTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val items = listOf(
    Triple(KidsModeTab.ISLANDS, "🏝️", res.getString(R.string.kids_mode_tab_islands)),
    Triple(KidsModeTab.REWARDS, "🏆", res.getString(R.string.kids_mode_tab_rewards)),
    Triple(KidsModeTab.PRACTICE_HUB, "🎒", res.getString(R.string.kids_mode_tab_hub))
  )

  Surface(
    shape = RoundedCornerShape(28.dp),
    color = if (isDark) Color(0xFF0C2637).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f),
    border = BorderStroke(
      width = 1.dp,
      color = if (isDark) Color(0xFF1E4E68) else Color(0xFF0D9488).copy(alpha = 0.22f)
    ),
    shadowElevation = 10.dp,
    modifier = modifier
      .widthIn(max = 460.dp)
      .fillMaxWidth()
      .testTag("kids_mode_bottom_dock")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items.forEach { (tab, emoji, label) ->
        val selected = activeTab == tab
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
          targetValue = if (pressed) 0.94f else 1f,
          animationSpec = tween(durationMillis = if (pressed) 100 else 160, easing = KidsDockEaseOut),
          label = "dockTabScale"
        )
        val pillBg by animateColorAsState(
          targetValue = if (selected) {
            Color(0xFF0F766E)
          } else {
            Color.Transparent
          },
          animationSpec = tween(durationMillis = 190, easing = KidsDockEaseOut),
          label = "dockTabBg"
        )
        val contentColor by animateColorAsState(
          targetValue = when {
            selected -> Color.White
            isDark -> Color(0xFFBAE6FD)
            else -> Color(0xFF334155)
          },
          animationSpec = tween(durationMillis = 190, easing = KidsDockEaseOut),
          label = "dockTabFg"
        )

        Surface(
          onClick = { onSelectTab(tab) },
          interactionSource = interaction,
          shape = RoundedCornerShape(22.dp),
          color = pillBg,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
            }
            .testTag("kids_dock_tab_${tab.name.lowercase()}")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (tab == KidsModeTab.REWARDS && badgeCount > 0) {
                "$label (${MemoryFlashcardManager.formatNumber(badgeCount, isArabic)})"
              } else {
                label
              },
              color = contentColor,
              fontSize = 12.sp,
              fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

@Composable
private fun KidsRewardsVaultTab(
  displayName: String,
  rewards: KidsRewardsState,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onEditNameClick: () -> Unit,
  onPracticeSurah: (Int) -> Unit
) {
  var subTab by remember { mutableStateOf(KidsRewardsSubTab.SURAH_BADGES) }
  var tierFilter by remember { mutableStateOf(KidsRewardsTierFilter.ALL) }

  val earnedSurahBadges = remember(rewards) { rewards.earnedSurahBadgesList }
  val filteredSurahBadges = remember(earnedSurahBadges, tierFilter) {
    when (tierFilter) {
      KidsRewardsTierFilter.ALL -> earnedSurahBadges
      KidsRewardsTierFilter.DIAMOND -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.DIAMOND }
      KidsRewardsTierFilter.GOLD -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.GOLD }
      KidsRewardsTierFilter.SILVER -> earnedSurahBadges.filter { it.tier == SurahBadgeTier.SILVER }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          if (isDark) listOf(Color(0xFF071B2A), Color(0xFF0B2538))
          else listOf(Color(0xFFFFFBEB), Color(0xFFF0FDF4))
        )
      ),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .testTag("kids_rewards_vault_tab"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item(key = "rewards_profile_hero") {
        LearnerProfileHeroCard(
          displayName = displayName,
          rewards = rewards,
          isArabic = isArabic,
          res = res,
          onEditNameClick = onEditNameClick
        )
      }

      item(key = "rewards_subtabs") {
        val badgesCountStr = MemoryFlashcardManager.formatNumber(earnedSurahBadges.size, isArabic)
        val milestonesCountStr = MemoryFlashcardManager.formatNumber(rewards.unlockedBadges.size, isArabic)
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("kids_rewards_subtabs")
        ) {
          SegmentedButton(
            selected = subTab == KidsRewardsSubTab.SURAH_BADGES,
            onClick = { subTab = KidsRewardsSubTab.SURAH_BADGES },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text(
              text = "🏅 ${res.getString(R.string.kids_achievements_tab_surah_badges)} ($badgesCountStr)",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          SegmentedButton(
            selected = subTab == KidsRewardsSubTab.MILESTONES,
            onClick = { subTab = KidsRewardsSubTab.MILESTONES },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
          ) {
            Text(
              text = "🎯 ${res.getString(R.string.kids_achievements_tab_milestones)} ($milestonesCountStr)",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      when (subTab) {
        KidsRewardsSubTab.SURAH_BADGES -> {
          item(key = "rewards_tier_filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val filters = listOf(
                Triple(KidsRewardsTierFilter.ALL, res.getString(R.string.kids_achievements_filter_all), earnedSurahBadges.size),
                Triple(KidsRewardsTierFilter.DIAMOND, "💎 ${res.getString(R.string.kids_achievements_filter_diamond)}", rewards.diamondBadgeCount),
                Triple(KidsRewardsTierFilter.GOLD, "🥇 ${res.getString(R.string.kids_achievements_filter_gold)}", rewards.goldBadgeCount),
                Triple(KidsRewardsTierFilter.SILVER, "🥈 ${res.getString(R.string.kids_achievements_filter_silver)}", rewards.silverBadgeCount)
              )
              items(filters, key = { it.first.name }) { (filter, label, count) ->
                FilterChip(
                  selected = tierFilter == filter,
                  onClick = { tierFilter = filter },
                  label = {
                    Text(
                      text = "$label (${MemoryFlashcardManager.formatNumber(count, isArabic)})",
                      fontWeight = FontWeight.SemiBold
                    )
                  },
                  modifier = Modifier.defaultMinSize(minHeight = 46.dp)
                )
              }
            }
          }

          if (filteredSurahBadges.isEmpty()) {
            item(key = "rewards_empty_badges") {
              EmptySurahBadgesCard(
                hasAnyBadges = earnedSurahBadges.isNotEmpty(),
                isArabic = isArabic,
                res = res,
                onPracticeSurah = onPracticeSurah
              )
            }
          } else {
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

        KidsRewardsSubTab.MILESTONES -> {
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
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsPracticeHubTab(
  displayName: String,
  rewards: KidsRewardsState,
  kidsState: KidsClassroomUiState,
  analysisSummaries: Map<Int, SurahAnalysisSummary>,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onEditNameClick: () -> Unit,
  onSelectQari: (KidsTeacherQari) -> Unit,
  onSelectDifficulty: (KidsDifficultyLevel) -> Unit,
  onSetAyahRepeat: (Int) -> Unit,
  onSetSurahRepeat: (Int) -> Unit,
  onToggleAutoNext: (Boolean) -> Unit,
  onToggleRepeatFromMistake: (Boolean) -> Unit,
  onOpenSurahAnalysis: (Int) -> Unit,
  onSwitchToNormalMode: () -> Unit
) {
  val surahsWithTrickyWords = remember(analysisSummaries) {
    analysisSummaries.values
      .filter { it.uniqueDifficultWordCount > 0 }
      .sortedByDescending { it.uniqueDifficultWordCount }
  }
  val defaultAnalysisSurah = surahsWithTrickyWords.firstOrNull()?.surah ?: kidsState.currentAyah.sura

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          if (isDark) listOf(Color(0xFF071B2A), Color(0xFF0C2638))
          else listOf(Color(0xFFF0FDF4), Color(0xFFEFF6FF))
        )
      ),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .testTag("kids_practice_hub_tab"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item(key = "hub_profile") {
        LearnerProfileHeroCard(
          displayName = displayName,
          rewards = rewards,
          isArabic = isArabic,
          res = res,
          onEditNameClick = onEditNameClick
        )
      }

      // Revision & Tricky Words Card
      item(key = "hub_revision") {
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = if (isDark) Color(0xFF0F2D3D) else Color.White,
          border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.28f)),
          shadowElevation = 2.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "📊 ${res.getString(R.string.kids_mode_hub_revision_title)}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold
            )
            Text(
              text = if (surahsWithTrickyWords.isEmpty()) {
                res.getString(R.string.kids_mode_hub_revision_empty)
              } else {
                res.getString(
                  R.string.kids_mode_hub_revision_summary,
                  MemoryFlashcardManager.formatNumber(surahsWithTrickyWords.size, isArabic)
                )
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (surahsWithTrickyWords.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                surahsWithTrickyWords.take(6).forEach { summary ->
                  val countStr = MemoryFlashcardManager.formatNumber(summary.uniqueDifficultWordCount, isArabic)
                  FilterChip(
                    selected = false,
                    onClick = { onOpenSurahAnalysis(summary.surah) },
                    label = {
                      Text(
                        text = "${summary.surahNameAr} ($countStr)",
                        fontWeight = FontWeight.Bold
                      )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                  )
                }
              }
            }
            OutlinedButton(
              onClick = { onOpenSurahAnalysis(defaultAnalysisSurah) },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("kids_hub_open_analysis_btn")
            ) {
              Text(
                text = "📊 ${res.getString(R.string.kids_mode_hub_open_analysis)}",
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // Kids Lesson Controls (Teacher Qari, Difficulty, Repeats, Mistake Retry, Auto-Next)
      item(key = "hub_lesson_settings") {
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = if (isDark) Color(0xFF0F2D3D) else Color.White,
          border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.25f)),
          shadowElevation = 2.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Text(
              text = "🎛️ ${res.getString(R.string.kids_mode_hub_settings_title)}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold
            )

            // Teacher Qari Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = res.getString(R.string.kids_teacher_qari_section_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                FilterChip(
                  selected = kidsState.selectedTeacherQari == KidsTeacherQari.HUSARY_MUALLIM,
                  onClick = { onSelectQari(KidsTeacherQari.HUSARY_MUALLIM) },
                  label = { Text(res.getString(R.string.kids_teacher_qari_husary), fontWeight = FontWeight.SemiBold) },
                  modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                )
                FilterChip(
                  selected = kidsState.selectedTeacherQari == KidsTeacherQari.MINSHAWI_TEACHER,
                  onClick = { onSelectQari(KidsTeacherQari.MINSHAWI_TEACHER) },
                  label = { Text(res.getString(R.string.kids_teacher_qari_minshawi), fontWeight = FontWeight.SemiBold) },
                  modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                )
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Difficulty Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = res.getString(R.string.kids_difficulty_section_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(
                  KidsDifficultyLevel.STARTER_70 to res.getString(R.string.kids_difficulty_70),
                  KidsDifficultyLevel.INTERMEDIATE_80 to res.getString(R.string.kids_difficulty_80),
                  KidsDifficultyLevel.PROFICIENT_90 to res.getString(R.string.kids_difficulty_90_proficient)
                ).forEach { (level, label) ->
                  FilterChip(
                    selected = kidsState.difficultyLevel == level,
                    onClick = { onSelectDifficulty(level) },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.defaultMinSize(minHeight = 46.dp)
                  )
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Ayah & Surah Repeat Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = res.getString(R.string.kids_repeat_ayah_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(1, 2, 3, 5, 7, 10).forEach { preset ->
                  FilterChip(
                    selected = kidsState.ayahRepeatTarget == preset,
                    onClick = { onSetAyahRepeat(preset) },
                    label = {
                      Text(
                        text = res.getString(
                          R.string.kids_repeat_times_format,
                          MemoryFlashcardManager.formatNumber(preset, isArabic)
                        ),
                        fontWeight = FontWeight.SemiBold
                      )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                  )
                }
              }

              Text(
                text = res.getString(R.string.kids_repeat_surah_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(1, 2, 3, 5, 10).forEach { preset ->
                  FilterChip(
                    selected = kidsState.surahRepeatTarget == preset,
                    onClick = { onSetSurahRepeat(preset) },
                    label = {
                      Text(
                        text = res.getString(
                          R.string.kids_repeat_times_format,
                          MemoryFlashcardManager.formatNumber(preset, isArabic)
                        ),
                        fontWeight = FontWeight.SemiBold
                      )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                  )
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Auto-Next & Repeat From First Mistake
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = res.getString(R.string.kids_classroom_auto_next),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
              )
              Switch(
                checked = kidsState.autoNextEnabled,
                onCheckedChange = onToggleAutoNext
              )
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Text(
                  text = res.getString(R.string.kids_repeat_from_mistake_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = res.getString(R.string.kids_repeat_from_mistake_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = kidsState.repeatFromFirstMistakeOnly,
                onCheckedChange = onToggleRepeatFromMistake
              )
            }
          }
        }
      }

      // Clean Switch Back to Normal Mode Card
      item(key = "hub_switch_normal") {
        Surface(
          onClick = onSwitchToNormalMode,
          shape = RoundedCornerShape(22.dp),
          color = if (isDark) Color(0xFF1A2A38) else Color.White,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("kids_hub_switch_normal_mode_card")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(text = "📖", fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = res.getString(R.string.kids_mode_switch_normal_btn),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
              )
              Text(
                text = res.getString(R.string.kids_mode_switch_normal_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Text(
              text = if (isArabic) "←" else "→",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }
  }
}
