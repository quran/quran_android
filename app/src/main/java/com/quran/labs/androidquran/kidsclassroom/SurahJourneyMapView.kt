package com.quran.labs.androidquran.kidsclassroom

import android.content.res.Resources
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import kotlin.math.sin

private val TrailGlowGold = Color(0xFFF59E0B)
private val TrailGlowEmerald = Color(0xFF10B981)
private val TrailUpcomingTint = Color(0xFF94A3B8)

private fun laneFractionForIndex(index: Int): Float =
  (0.50f + 0.23f * sin(index * 1.05).toFloat()).coerceIn(0.24f, 0.76f)

/**
 * Emits all items for the visual **Surah Journey** map inside a parent [LazyColumn] so scrolling
 * across the Profile header, Journey controls, winding S-curve stepping stones, and Milestone
 * Checkpoint Gates remains completely smooth with zero nested scroll conflicts.
 */
internal fun LazyListScope.surahJourneyMapItems(
  scope: SurahJourneyScope,
  selectedJuzForDrillDown: Int?,
  rewards: KidsRewardsState,
  isArabic: Boolean,
  res: Resources,
  onSelectScope: (SurahJourneyScope) -> Unit,
  onOpenJuzDrillDown: (Int) -> Unit,
  onBackToAll30Juz: () -> Unit,
  onPracticeSurah: (Int) -> Unit
) {
  // 1. Scope selector chips (Juz 'Amma / Juz 29 / Full Quran 30 Juz)
  item(key = "journey_scope_chips") {
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("journey_scope_chips")
    ) {
      val scopes = listOf(
        SurahJourneyScope.JUZ_AMMA to "🕌 ${res.getString(R.string.kids_journey_scope_juz_amma)}",
        SurahJourneyScope.JUZ_TABARAK to "🌙 ${res.getString(R.string.kids_journey_scope_juz_29)}",
        SurahJourneyScope.FULL_QURAN to "🗺️ ${res.getString(R.string.kids_journey_scope_full_quran)}"
      )
      items(scopes, key = { it.first.name }) { (itemScope, label) ->
        FilterChip(
          selected = scope == itemScope,
          onClick = { onSelectScope(itemScope) },
          label = { Text(text = label, fontWeight = FontWeight.Bold) },
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("journey_scope_${itemScope.name.lowercase()}")
        )
      }
    }
  }

  val showFullQuran30Juz = scope == SurahJourneyScope.FULL_QURAN && selectedJuzForDrillDown == null

  if (showFullQuran30Juz) {
    val quranPlan = SurahJourneyPlanner.buildFullQuranJourneyPlan(rewards)

    item(key = "journey_quran_summary") {
      JourneyProgressBanner(
        title = res.getString(R.string.kids_journey_scope_full_quran),
        primaryProgressText = res.getString(
          R.string.kids_journey_surahs_progress,
          MemoryFlashcardManager.formatNumber(quranPlan.completedSurahsTotal, isArabic),
          MemoryFlashcardManager.formatNumber(114, isArabic)
        ),
        secondaryProgressText = res.getString(
          R.string.kids_journey_juz_progress,
          MemoryFlashcardManager.formatNumber(quranPlan.completedJuzCount, isArabic),
          MemoryFlashcardManager.formatNumber(30, isArabic)
        ),
        gatesText = res.getString(
          R.string.kids_journey_gates_unlocked,
          MemoryFlashcardManager.formatNumber(quranPlan.unlockedGateCount, isArabic),
          MemoryFlashcardManager.formatNumber(quranPlan.gates.size, isArabic)
        ),
        progressPercent = quranPlan.progressPercent,
        isArabic = isArabic,
        res = res,
        onContinueClick = { onOpenJuzDrillDown(quranPlan.nextRecommendedJuz) }
      )
    }

    itemsIndexed(
      items = quranPlan.trailItems,
      key = { _, item -> item.key }
    ) { index, item ->
      val prevItem = quranPlan.trailItems.getOrNull(index - 1)
      val nextItem = quranPlan.trailItems.getOrNull(index + 1)

      when (item) {
        is FullQuranTrailItem.JuzStep -> {
          val curLane = laneFractionForIndex(index)
          val prevLane = when (prevItem) {
            null -> null
            is FullQuranTrailItem.MilestoneGate -> 0.50f
            is FullQuranTrailItem.JuzStep -> laneFractionForIndex(index - 1)
          }
          val nextLane = when (nextItem) {
            null -> null
            is FullQuranTrailItem.MilestoneGate -> 0.50f
            is FullQuranTrailItem.JuzStep -> laneFractionForIndex(index + 1)
          }
          val topLit = item.node.isCompleted || item.node.status == JourneyNodeStatus.IN_PROGRESS || item.node.status == JourneyNodeStatus.NEXT_UP
          val bottomLit = item.node.isCompleted

          JuzTrailStepRow(
            node = item.node,
            laneFraction = curLane,
            prevLaneFraction = prevLane,
            nextLaneFraction = nextLane,
            isTopPathLit = topLit,
            isBottomPathLit = bottomLit,
            isArabic = isArabic,
            res = res,
            onClick = { onOpenJuzDrillDown(item.node.juzSpec.juzNumber) }
          )
        }
        is FullQuranTrailItem.MilestoneGate -> {
          MilestoneGateTrailRow(
            gate = item.gate,
            hasTopConnector = prevItem != null,
            hasBottomConnector = nextItem != null,
            isArabic = isArabic,
            res = res
          )
        }
      }
    }
  } else {
    val surahPlan = SurahJourneyPlanner.buildSurahJourneyPlan(
      scope = scope,
      rewards = rewards,
      selectedJuzNumber = selectedJuzForDrillDown
    )

    if (selectedJuzForDrillDown != null) {
      item(key = "journey_back_30_juz") {
        OutlinedButton(
          onClick = onBackToAll30Juz,
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("journey_back_to_30_juz_btn")
        ) {
          val arrow = if (isArabic) "→" else "←"
          Text(
            text = "$arrow ${res.getString(R.string.kids_journey_back_to_30_juz)}",
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    item(key = "journey_surah_summary_${surahPlan.scope}_${surahPlan.selectedJuzNumber}") {
      JourneyProgressBanner(
        title = if (isArabic) surahPlan.titleAr else surahPlan.titleEn,
        primaryProgressText = res.getString(
          R.string.kids_journey_surahs_progress,
          MemoryFlashcardManager.formatNumber(surahPlan.completedSurahCount, isArabic),
          MemoryFlashcardManager.formatNumber(surahPlan.totalSurahCount, isArabic)
        ),
        secondaryProgressText = "🏅 ${MemoryFlashcardManager.formatNumber(surahPlan.badgedSurahCount, isArabic)}",
        gatesText = res.getString(
          R.string.kids_journey_gates_unlocked,
          MemoryFlashcardManager.formatNumber(surahPlan.unlockedGateCount, isArabic),
          MemoryFlashcardManager.formatNumber(surahPlan.gates.size, isArabic)
        ),
        progressPercent = surahPlan.progressPercent,
        isArabic = isArabic,
        res = res,
        onContinueClick = { onPracticeSurah(surahPlan.nextRecommendedSurah) }
      )
    }

    itemsIndexed(
      items = surahPlan.trailItems,
      key = { _, item -> item.key }
    ) { index, item ->
      val prevItem = surahPlan.trailItems.getOrNull(index - 1)
      val nextItem = surahPlan.trailItems.getOrNull(index + 1)

      when (item) {
        is SurahTrailItem.SurahStep -> {
          val curLane = laneFractionForIndex(index)
          val prevLane = when (prevItem) {
            null -> null
            is SurahTrailItem.MilestoneGate -> 0.50f
            is SurahTrailItem.SurahStep -> laneFractionForIndex(index - 1)
          }
          val nextLane = when (nextItem) {
            null -> null
            is SurahTrailItem.MilestoneGate -> 0.50f
            is SurahTrailItem.SurahStep -> laneFractionForIndex(index + 1)
          }
          val topLit = item.node.isCompleted || item.node.status == JourneyNodeStatus.IN_PROGRESS || item.node.status == JourneyNodeStatus.NEXT_UP
          val bottomLit = item.node.isCompleted

          SurahTrailStepRow(
            node = item.node,
            laneFraction = curLane,
            prevLaneFraction = prevLane,
            nextLaneFraction = nextLane,
            isTopPathLit = topLit,
            isBottomPathLit = bottomLit,
            isArabic = isArabic,
            res = res,
            onClick = { onPracticeSurah(item.node.surahNumber) }
          )
        }
        is SurahTrailItem.MilestoneGate -> {
          MilestoneGateTrailRow(
            gate = item.gate,
            hasTopConnector = prevItem != null,
            hasBottomConnector = nextItem != null,
            isArabic = isArabic,
            res = res
          )
        }
      }
    }
  }
}

@Composable
private fun JourneyProgressBanner(
  title: String,
  primaryProgressText: String,
  secondaryProgressText: String,
  gatesText: String,
  progressPercent: Int,
  isArabic: Boolean,
  res: Resources,
  onContinueClick: () -> Unit
) {
  val pctText = "${MemoryFlashcardManager.formatNumber(progressPercent, isArabic)}%"

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, TrailGlowEmerald.copy(alpha = 0.42f)),
    tonalElevation = 2.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("journey_progress_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Circular Journey Progress Ring
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(64.dp)
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeW = 7.dp.toPx()
          val inset = strokeW / 2f
          val arcSize = Size(size.width - strokeW, size.height - strokeW)
          drawArc(
            color = TrailUpcomingTint.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
          )
          drawArc(
            brush = Brush.sweepGradient(listOf(TrailGlowEmerald, TrailGlowGold, TrailGlowEmerald)),
            startAngle = -90f,
            sweepAngle = (360f * (progressPercent / 100f)).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
          )
        }
        Text(
          text = pctText,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "$primaryProgressText · $secondaryProgressText",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "🚩 $gatesText",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = TrailGlowEmerald
        )
      }

      Button(
        onClick = onContinueClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = TrailGlowEmerald,
          contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("journey_continue_btn")
      ) {
        Text(
          text = res.getString(R.string.kids_journey_continue_btn),
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }
    }
  }
}

@Composable
private fun SurahTrailStepRow(
  node: SurahJourneyNode,
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean,
  isArabic: Boolean,
  res: Resources,
  onClick: () -> Unit
) {
  val surahNumText = MemoryFlashcardManager.formatNumber(node.surahNumber, isArabic)
  val masteredText = MemoryFlashcardManager.formatNumber(node.masteredAyat, isArabic)
  val totalText = MemoryFlashcardManager.formatNumber(node.totalAyat, isArabic)
  val starsText = MemoryFlashcardManager.formatNumber(node.starsEarned, isArabic)

  val isActiveBeacon = node.status == JourneyNodeStatus.NEXT_UP || node.status == JourneyNodeStatus.IN_PROGRESS
  val beaconPulse = if (isActiveBeacon) {
    val infinite = rememberInfiniteTransition(label = "beaconPulse")
    val p by infinite.animateFloat(
      initialValue = 0.22f,
      targetValue = 0.62f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "beaconAlpha"
    )
    p
  } else {
    0f
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(106.dp)
      .testTag("journey_surah_node_${node.surahNumber}")
  ) {
    val rowWidth = maxWidth
    val nodeSize = 66.dp
    val centerX = rowWidth * laneFraction
    val placeCardOnRight = laneFraction <= 0.52f

    // Winding S-curve trail Canvas behind the stepping stone
    TrailConnectorCanvas(
      laneFraction = laneFraction,
      prevLaneFraction = prevLaneFraction,
      nextLaneFraction = nextLaneFraction,
      isTopPathLit = isTopPathLit,
      isBottomPathLit = isBottomPathLit
    )

    // Clickable Stepping-Stone Medallion
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .offset(x = (centerX - nodeSize / 2).coerceIn(4.dp, rowWidth - nodeSize - 4.dp), y = 20.dp)
        .size(nodeSize)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    ) {
      when {
        node.badge != null -> {
          val accText = "${MemoryFlashcardManager.formatNumber(node.badge.bestAccuracyPercent, isArabic)}%"
          SurahBadgeMedallion(
            tier = node.badge.tier,
            centerLabel = accText,
            size = nodeSize,
            animateGlow = node.badge.tier == SurahBadgeTier.DIAMOND
          )
        }
        node.status == JourneyNodeStatus.COMPLETED_STARS -> {
          Surface(
            shape = CircleShape,
            color = TrailGlowEmerald,
            border = BorderStroke(3.dp, Color(0xFFA7F3D0)),
            shadowElevation = 4.dp,
            modifier = Modifier.size(60.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "✓",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }
        }
        isActiveBeacon -> {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              drawCircle(
                color = TrailGlowGold.copy(alpha = beaconPulse),
                radius = size.minDimension / 2f
              )
              val strokeW = 4.5.dp.toPx()
              val inset = 5.dp.toPx()
              val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
              drawArc(
                color = TrailGlowEmerald.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
              )
              val sweep = if (node.progressFraction > 0f) 360f * node.progressFraction else 360f
              drawArc(
                color = if (node.progressFraction > 0f) TrailGlowEmerald else TrailGlowGold,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
              )
            }
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(48.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = surahNumText,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.ExtraBold
                )
              }
            }
          }
        }
        else -> {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(54.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = surahNumText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    // Companion Surah Info Card beside the stepping stone
    val cardWidth = (rowWidth * 0.44f).coerceIn(136.dp, 210.dp)
    val cardOffsetX = if (placeCardOnRight) {
      (centerX + nodeSize / 2 + 8.dp).coerceAtMost(rowWidth - cardWidth - 4.dp)
    } else {
      (centerX - nodeSize / 2 - cardWidth - 8.dp).coerceAtLeast(4.dp)
    }

    val cardBorderColor = when {
      node.badge != null -> visualStyleForTier(node.badge.tier).cardAccent.copy(alpha = 0.55f)
      node.status == JourneyNodeStatus.COMPLETED_STARS -> TrailGlowEmerald.copy(alpha = 0.55f)
      isActiveBeacon -> TrailGlowGold.copy(alpha = 0.85f)
      else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(16.dp),
      color = if (isActiveBeacon) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
      border = BorderStroke(if (isActiveBeacon) 1.5.dp else 1.dp, cardBorderColor),
      shadowElevation = if (isActiveBeacon) 3.dp else 0.dp,
      modifier = Modifier
        .offset(x = cardOffsetX, y = 16.dp)
        .width(cardWidth)
        .defaultMinSize(minHeight = 72.dp)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = node.surahNameAr,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          if (node.badge != null) {
            Text(text = node.badge.tier.emoji, fontSize = 14.sp)
          } else if (node.starsEarned > 0) {
            Text(
              text = "⭐$starsText",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFD97706)
            )
          }
        }

        val subLine = when {
          node.badge != null -> {
            val tierName = if (isArabic) node.badge.tier.titleAr else node.badge.tier.titleEn
            tierName
          }
          node.status == JourneyNodeStatus.NEXT_UP -> res.getString(R.string.kids_journey_next_step_pill)
          node.status == JourneyNodeStatus.IN_PROGRESS -> res.getString(R.string.kids_journey_in_progress_pill)
          else -> "#$surahNumText"
        }
        Text(
          text = subLine,
          fontSize = 11.sp,
          fontWeight = if (isActiveBeacon || node.badge != null) FontWeight.Bold else FontWeight.Medium,
          color = when {
            node.badge != null -> visualStyleForTier(node.badge.tier).cardAccent
            isActiveBeacon -> TrailGlowEmerald
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Text(
          text = res.getString(R.string.kids_journey_ayat_progress, masteredText, totalText),
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun JuzTrailStepRow(
  node: JuzOverviewNode,
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean,
  isArabic: Boolean,
  res: Resources,
  onClick: () -> Unit
) {
  val juzNumText = MemoryFlashcardManager.formatNumber(node.juzSpec.juzNumber, isArabic)
  val doneStr = MemoryFlashcardManager.formatNumber(node.completedSurahs, isArabic)
  val totalStr = MemoryFlashcardManager.formatNumber(node.totalSurahs, isArabic)
  val isActiveBeacon = node.status == JourneyNodeStatus.NEXT_UP || node.status == JourneyNodeStatus.IN_PROGRESS

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(106.dp)
      .testTag("journey_juz_node_${node.juzSpec.juzNumber}")
  ) {
    val rowWidth = maxWidth
    val nodeSize = 66.dp
    val centerX = rowWidth * laneFraction
    val placeCardOnRight = laneFraction <= 0.52f

    TrailConnectorCanvas(
      laneFraction = laneFraction,
      prevLaneFraction = prevLaneFraction,
      nextLaneFraction = nextLaneFraction,
      isTopPathLit = isTopPathLit,
      isBottomPathLit = isBottomPathLit
    )

    // Juz Island Medallion
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .offset(x = (centerX - nodeSize / 2).coerceIn(4.dp, rowWidth - nodeSize - 4.dp), y = 20.dp)
        .size(nodeSize)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    ) {
      if (node.isCompleted && node.bestTierInJuz != SurahBadgeTier.NONE) {
        SurahBadgeMedallion(
          tier = node.bestTierInJuz,
          centerLabel = juzNumText,
          size = nodeSize,
          animateGlow = node.bestTierInJuz == SurahBadgeTier.DIAMOND
        )
      } else {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 5.dp.toPx()
            val inset = 5.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
              color = TrailUpcomingTint.copy(alpha = 0.25f),
              startAngle = -90f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = Offset(inset, inset),
              size = arcSize,
              style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            if (node.progressFraction > 0f) {
              drawArc(
                color = TrailGlowEmerald,
                startAngle = -90f,
                sweepAngle = 360f * node.progressFraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
              )
            }
          }
          Surface(
            shape = CircleShape,
            color = if (node.isCompleted) TrailGlowEmerald
            else if (isActiveBeacon) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
              1.5.dp,
              if (isActiveBeacon) TrailGlowGold else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.size(48.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = juzNumText,
                color = if (node.isCompleted) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }
        }
      }
    }

    val cardWidth = (rowWidth * 0.45f).coerceIn(142.dp, 216.dp)
    val cardOffsetX = if (placeCardOnRight) {
      (centerX + nodeSize / 2 + 8.dp).coerceAtMost(rowWidth - cardWidth - 4.dp)
    } else {
      (centerX - nodeSize / 2 - cardWidth - 8.dp).coerceAtLeast(4.dp)
    }

    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(16.dp),
      color = if (isActiveBeacon) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
      border = BorderStroke(
        width = if (isActiveBeacon) 1.5.dp else 1.dp,
        color = if (node.isCompleted) TrailGlowEmerald.copy(alpha = 0.6f)
        else if (isActiveBeacon) TrailGlowGold.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
      ),
      modifier = Modifier
        .offset(x = cardOffsetX, y = 14.dp)
        .width(cardWidth)
        .defaultMinSize(minHeight = 76.dp)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${res.getString(R.string.kids_journey_juz_label, juzNumText)} · ${node.juzSpec.nameAr}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
        }
        Text(
          text = res.getString(R.string.kids_journey_juz_surahs_count, doneStr, totalStr),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (node.isCompleted) TrailGlowEmerald else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "${res.getString(R.string.kids_journey_open_juz_hint)} ${if (isArabic) "←" else "→"}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun MilestoneGateTrailRow(
  gate: JourneyMilestoneGate,
  hasTopConnector: Boolean,
  hasBottomConnector: Boolean,
  isArabic: Boolean,
  res: Resources
) {
  val title = if (isArabic) gate.titleAr else gate.titleEn
  val subtitle = if (isArabic) gate.subtitleAr else gate.subtitleEn
  val curStr = MemoryFlashcardManager.formatNumber(gate.currentCount, isArabic)
  val reqStr = MemoryFlashcardManager.formatNumber(gate.requiredCount, isArabic)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .testTag("journey_milestone_gate_${gate.id}")
  ) {
    // Center spine connector behind gate banner
    Canvas(modifier = Modifier.matchParentSize()) {
      val cx = size.width / 2f
      val color = if (gate.isUnlocked) TrailGlowGold else TrailUpcomingTint.copy(alpha = 0.45f)
      val strokeW = 5.dp.toPx()
      if (hasTopConnector) {
        drawLine(
          color = color,
          start = Offset(cx, 0f),
          end = Offset(cx, size.height * 0.25f),
          strokeWidth = strokeW
        )
      }
      if (hasBottomConnector) {
        drawLine(
          color = color,
          start = Offset(cx, size.height * 0.75f),
          end = Offset(cx, size.height),
          strokeWidth = strokeW
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(20.dp),
      color = if (gate.isUnlocked) {
        Color(0xFF065F46)
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
      border = BorderStroke(
        width = if (gate.isUnlocked) 2.dp else 1.dp,
        color = if (gate.isUnlocked) TrailGlowGold else MaterialTheme.colorScheme.outlineVariant
      ),
      shadowElevation = if (gate.isUnlocked) 5.dp else 0.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
              if (gate.isUnlocked) TrailGlowGold.copy(alpha = 0.25f)
              else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
          Text(text = gate.emoji, fontSize = 24.sp)
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = title,
              color = if (gate.isUnlocked) Color.White else MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (gate.isUnlocked) TrailGlowGold else MaterialTheme.colorScheme.surface
            ) {
              Text(
                text = if (gate.isUnlocked) {
                  "✓ ${res.getString(R.string.kids_journey_gate_unlocked)}"
                } else {
                  "$curStr / $reqStr"
                },
                color = if (gate.isUnlocked) Color(0xFF1E1B4B) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          Text(
            text = subtitle,
            color = if (gate.isUnlocked) Color.White.copy(alpha = 0.88f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          if (!gate.isUnlocked) {
            LinearProgressIndicator(
              progress = { gate.progressFraction },
              color = TrailGlowEmerald,
              trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TrailConnectorCanvas(
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val midY = h / 2f
    val curX = w * laneFraction
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

    fun drawCurve(
      startX: Float,
      startY: Float,
      endX: Float,
      endY: Float,
      lit: Boolean
    ) {
      val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(
          startX,
          (startY + endY) / 2f,
          endX,
          (startY + endY) / 2f,
          endX,
          endY
        )
      }
      if (lit) {
        drawPath(
          path = path,
          color = TrailGlowGold.copy(alpha = 0.28f),
          style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(TrailGlowGold, TrailGlowEmerald)),
          style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
        )
      } else {
        drawPath(
          path = path,
          color = TrailUpcomingTint.copy(alpha = 0.45f),
          style = Stroke(
            width = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = dashEffect
          )
        )
      }
    }

    if (prevLaneFraction != null) {
      val topEntryX = (w * prevLaneFraction + curX) / 2f
      drawCurve(topEntryX, 0f, curX, midY, isTopPathLit)
    }
    if (nextLaneFraction != null) {
      val bottomExitX = (curX + w * nextLaneFraction) / 2f
      drawCurve(curX, midY, bottomExitX, h, isBottomPathLit)
    }
  }
}
