package com.quran.labs.androidquran.kidsclassroom

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val EaseOutExpo = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val LagoonGold = Color(0xFFF59E0B)
private val LagoonEmerald = Color(0xFF10B981)
private val LagoonTeal = Color(0xFF0D9488)
private val LagoonSky = Color(0xFF0284C7)
private val IslandSandLight = Color(0xFFFDE68A)
private val IslandSandWarm = Color(0xFFF5C97B)
private val IslandGrassBright = Color(0xFF34D399)
private val IslandGrassDeep = Color(0xFF059669)
private val IslandWaterShallow = Color(0xFF67E8F9)
private val IslandUpcomingSlate = Color(0xFF94A3B8)

private fun islandLaneFraction(index: Int): Float =
  (0.50f + 0.22f * sin(index * 1.05).toFloat()).coerceIn(0.25f, 0.75f)

/**
 * Interactive **Progress Islands** archipelago map that serves as the playful home of Full Kids
 * Mode and the interactive destination whenever the child presses Back from a Kids Mode lesson.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsProgressIslandsView(
  rewards: KidsRewardsState,
  returnEvent: KidsIslandReturnEvent?,
  isArabic: Boolean,
  res: Resources,
  onConsumeReturnEvent: (Long) -> Unit,
  onLaunchSurah: (surah: Int, launchMode: KidsLessonLaunchMode) -> Unit,
  onOpenSurahAnalysis: (surah: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val isDark = isSystemInDarkTheme()
  val haptics = LocalHapticFeedback.current
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val analysisSummaries by KidsSurahAnalysisStore.summaries.collectAsState()

  var scope by remember { mutableStateOf(SurahJourneyScope.JUZ_AMMA) }
  var selectedJuzDrillDown by remember { mutableStateOf<Int?>(null) }
  var previewedNode by remember { mutableStateOf<SurahJourneyNode?>(null) }
  var highlightedReturnSurah by remember { mutableStateOf<Int?>(null) }
  var activeReturnBanner by remember { mutableStateOf<KidsIslandReturnEvent?>(null) }
  var returnBurstNonce by remember { mutableStateOf(0) }

  // Back inside a drilled-down Juz sails back to the 30-Juz archipelago view first
  BackHandler(enabled = selectedJuzDrillDown != null) {
    selectedJuzDrillDown = null
  }

  val showFullQuran30Juz = scope == SurahJourneyScope.FULL_QURAN && selectedJuzDrillDown == null
  val quranPlan = remember(rewards, showFullQuran30Juz) {
    if (showFullQuran30Juz) SurahJourneyPlanner.buildFullQuranJourneyPlan(rewards) else null
  }
  val surahPlan = remember(scope, selectedJuzDrillDown, rewards, showFullQuran30Juz) {
    if (!showFullQuran30Juz) {
      SurahJourneyPlanner.buildSurahJourneyPlan(
        scope = scope,
        rewards = rewards,
        selectedJuzNumber = selectedJuzDrillDown
      )
    } else {
      null
    }
  }

  // Handle interactive arrival when returning from a Kids Mode Mushaf lesson
  LaunchedEffect(returnEvent?.eventId) {
    val event = returnEvent ?: return@LaunchedEffect
    val (targetScope, targetJuz) = KidsModeNavigationState.resolveArchipelagoForSurah(event.surahNumber)
    scope = targetScope
    selectedJuzDrillDown = targetJuz
    highlightedReturnSurah = event.surahNumber
    activeReturnBanner = event
    returnBurstNonce += 1
    onConsumeReturnEvent(event.eventId)

    // Scroll smoothly right to the island the child just sailed back from
    val targetPlan = SurahJourneyPlanner.buildSurahJourneyPlan(
      scope = targetScope,
      rewards = rewards,
      selectedJuzNumber = targetJuz
    )
    val trailIdx = targetPlan.trailItems.indexOfFirst { item ->
      item is SurahTrailItem.SurahStep && item.node.surahNumber == event.surahNumber
    }
    if (trailIdx >= 0) {
      val headerItemsOffset = if (targetJuz != null) 3 else 2
      val targetListIndex = (trailIdx + headerItemsOffset - 1).coerceAtLeast(0)
      delay(60L)
      try {
        listState.animateScrollToItem(targetListIndex)
      } catch (_: Exception) {
        // Non-fatal if composition changed mid-scroll
      }
    }

    // Auto-clear the floating ribbon after a calm window (non-blocking)
    delay(5500L)
    if (activeReturnBanner?.eventId == event.eventId) {
      activeReturnBanner = null
    }
  }

  val oceanGradient = if (isDark) {
    listOf(Color(0xFF071B2A), Color(0xFF0C2E42), Color(0xFF0D3D47))
  } else {
    listOf(Color(0xFFE0F7FA), Color(0xFFCFFAFE), Color(0xFFD1FAE5))
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(oceanGradient))
      .testTag("kids_progress_islands_view")
  ) {
    // Subtle sunlit/starry ocean shimmer backdrop
    OceanAtmosphereBackdrop(isDark = isDark)

    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .align(Alignment.TopCenter),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 10.dp,
        bottom = 112.dp
      ),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Archipelago Sea Selector Pills
      item(key = "islands_archipelago_chips") {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("islands_scope_chips")
        ) {
          val scopes = listOf(
            SurahJourneyScope.JUZ_AMMA to "🏝️ ${res.getString(R.string.kids_journey_scope_juz_amma)}",
            SurahJourneyScope.JUZ_TABARAK to "🌙 ${res.getString(R.string.kids_journey_scope_juz_29)}",
            SurahJourneyScope.FULL_QURAN to "🗺️ ${res.getString(R.string.kids_journey_scope_full_quran)}"
          )
          items(scopes, key = { it.first.name }) { (itemScope, label) ->
            val selected = scope == itemScope
            ArchipelagoPill(
              label = label,
              selected = selected,
              isDark = isDark,
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope = itemScope
                selectedJuzDrillDown = null
              },
              testTag = "islands_scope_${itemScope.name.lowercase()}"
            )
          }
        }
      }

      if (showFullQuran30Juz && quranPlan != null) {
        // 30-Juz Seas Summary Compass
        item(key = "islands_full_quran_compass") {
          IslandAdventureCompassCard(
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
            ctaLabel = res.getString(R.string.kids_mode_sail_next_btn),
            isArabic = isArabic,
            isDark = isDark,
            onContinueClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              selectedJuzDrillDown = quranPlan.nextRecommendedJuz
            }
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
              val curLane = islandLaneFraction(index)
              val prevLane = when (prevItem) {
                null -> null
                is FullQuranTrailItem.MilestoneGate -> 0.50f
                is FullQuranTrailItem.JuzStep -> islandLaneFraction(index - 1)
              }
              val nextLane = when (nextItem) {
                null -> null
                is FullQuranTrailItem.MilestoneGate -> 0.50f
                is FullQuranTrailItem.JuzStep -> islandLaneFraction(index + 1)
              }
              val topLit = item.node.isCompleted ||
                item.node.status == JourneyNodeStatus.IN_PROGRESS ||
                item.node.status == JourneyNodeStatus.NEXT_UP
              val bottomLit = item.node.isCompleted

              JuzArchipelagoIslandRow(
                node = item.node,
                laneFraction = curLane,
                prevLaneFraction = prevLane,
                nextLaneFraction = nextLane,
                isTopPathLit = topLit,
                isBottomPathLit = bottomLit,
                isArabic = isArabic,
                isDark = isDark,
                res = res,
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  selectedJuzDrillDown = item.node.juzSpec.juzNumber
                }
              )
            }
            is FullQuranTrailItem.MilestoneGate -> {
              IslandTreasureGateRow(
                gate = item.gate,
                hasTopConnector = prevItem != null,
                hasBottomConnector = nextItem != null,
                isArabic = isArabic,
                isDark = isDark,
                res = res
              )
            }
          }
        }
      } else if (surahPlan != null) {
        if (selectedJuzDrillDown != null) {
          item(key = "islands_back_to_30_seas") {
            OutlinedButton(
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedJuzDrillDown = null
              },
              shape = RoundedCornerShape(999.dp),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isDark) Color(0xFF0F2D3D) else Color.White.copy(alpha = 0.85f)
              ),
              modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .testTag("islands_back_to_30_juz_btn")
            ) {
              val arrow = if (isArabic) "→" else "←"
              Text(
                text = "$arrow ${res.getString(R.string.kids_journey_back_to_30_juz)}",
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        item(key = "islands_surah_compass_${surahPlan.scope}_${surahPlan.selectedJuzNumber}") {
          IslandAdventureCompassCard(
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
            ctaLabel = res.getString(R.string.kids_mode_sail_next_btn),
            isArabic = isArabic,
            isDark = isDark,
            onContinueClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onLaunchSurah(surahPlan.nextRecommendedSurah, KidsLessonLaunchMode.OPEN_READY)
            }
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
              val curLane = islandLaneFraction(index)
              val prevLane = when (prevItem) {
                null -> null
                is SurahTrailItem.MilestoneGate -> 0.50f
                is SurahTrailItem.SurahStep -> islandLaneFraction(index - 1)
              }
              val nextLane = when (nextItem) {
                null -> null
                is SurahTrailItem.MilestoneGate -> 0.50f
                is SurahTrailItem.SurahStep -> islandLaneFraction(index + 1)
              }
              val topLit = item.node.isCompleted ||
                item.node.status == JourneyNodeStatus.IN_PROGRESS ||
                item.node.status == JourneyNodeStatus.NEXT_UP
              val bottomLit = item.node.isCompleted
              val isReturnedIsland = highlightedReturnSurah == item.node.surahNumber
              val showBoatHere = isReturnedIsland ||
                (highlightedReturnSurah == null && item.node.surahNumber == surahPlan.nextRecommendedSurah)

              SurahIslandStepRow(
                node = item.node,
                laneFraction = curLane,
                prevLaneFraction = prevLane,
                nextLaneFraction = nextLane,
                isTopPathLit = topLit,
                isBottomPathLit = bottomLit,
                showExplorerBoat = showBoatHere,
                isReturningHere = isReturnedIsland,
                returnBurstNonce = returnBurstNonce,
                starsGainedOnReturn = if (isReturnedIsland) activeReturnBanner?.starsGained ?: 0 else 0,
                isArabic = isArabic,
                isDark = isDark,
                res = res,
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  activeReturnBanner = null
                  previewedNode = item.node
                }
              )
            }
            is SurahTrailItem.MilestoneGate -> {
              IslandTreasureGateRow(
                gate = item.gate,
                hasTopConnector = prevItem != null,
                hasBottomConnector = nextItem != null,
                isArabic = isArabic,
                isDark = isDark,
                res = res
              )
            }
          }
        }
      }
    }

    // Floating, non-blocking Welcome-Back Sea Ribbon when returning from a lesson
    AnimatedVisibility(
      visible = activeReturnBanner != null,
      enter = fadeIn(tween(220, easing = EaseOutExpo)) +
        slideInVertically(tween(240, easing = EaseOutExpo)) { -it / 2 },
      exit = fadeOut(tween(160, easing = EaseOutExpo)) +
        slideOutVertically(tween(180, easing = EaseOutExpo)) { -it / 2 },
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .widthIn(max = 640.dp)
    ) {
      val bannerEvent = activeReturnBanner
      if (bannerEvent != null) {
        val nextRecommendedSurah = surahPlan?.nextRecommendedSurah ?: bannerEvent.surahNumber
        ReturnArrivalRibbon(
          event = bannerEvent,
          nextRecommendedSurah = nextRecommendedSurah,
          isArabic = isArabic,
          res = res,
          onSailNext = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            activeReturnBanner = null
            onLaunchSurah(nextRecommendedSurah, KidsLessonLaunchMode.OPEN_READY)
          },
          onDismiss = { activeReturnBanner = null }
        )
      }
    }
  }

  // Interactive Island Preview & Launch Sheet (one entrance per container; snappy 220ms feel)
  val selectedNode = previewedNode
  if (selectedNode != null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val difficultWordsCount = analysisSummaries[selectedNode.surahNumber]?.uniqueDifficultWordCount ?: 0

    ModalBottomSheet(
      onDismissRequest = { previewedNode = null },
      sheetState = sheetState,
      containerColor = if (isDark) Color(0xFF0E2636) else Color(0xFFFFFDF8),
      scrimColor = Color.Black.copy(alpha = 0.36f)
    ) {
      IslandLaunchSheetContent(
        node = selectedNode,
        difficultWordsCount = difficultWordsCount,
        isArabic = isArabic,
        isDark = isDark,
        res = res,
        onListenAndRepeat = {
          coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            previewedNode = null
            onLaunchSurah(selectedNode.surahNumber, KidsLessonLaunchMode.LISTEN_AND_REPEAT)
          }
        },
        onReciteNow = {
          coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            previewedNode = null
            onLaunchSurah(selectedNode.surahNumber, KidsLessonLaunchMode.RECITE_AND_REVEAL)
          }
        },
        onReviewTrickyWords = {
          coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            previewedNode = null
            onOpenSurahAnalysis(selectedNode.surahNumber)
          }
        }
      )
    }
  }
}

@Composable
private fun OceanAtmosphereBackdrop(isDark: Boolean) {
  val infinite = rememberInfiniteTransition(label = "oceanWave")
  val drift by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "waveDrift"
  )

  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val waveColor = if (isDark) {
      Color(0xFF38BDF8).copy(alpha = 0.08f)
    } else {
      Color.White.copy(alpha = 0.42f)
    }

    // Soft distant horizon sunlight / moonlight glow
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          if (isDark) Color(0xFFFDE68A).copy(alpha = 0.09f) else Color(0xFFFEF08A).copy(alpha = 0.35f),
          Color.Transparent
        ),
        center = Offset(w * 0.85f, h * 0.06f),
        radius = w * 0.45f
      ),
      radius = w * 0.45f,
      center = Offset(w * 0.85f, h * 0.06f)
    )

    // Subtle horizontal lagoon wave glints
    val waveCount = 7
    val spacing = h / (waveCount + 1)
    for (i in 1..waveCount) {
      val y = spacing * i
      val xShift = (drift - 0.5f) * 28.dp.toPx() * if (i % 2 == 0) 1f else -1f
      val path = Path().apply {
        val startX = -24.dp.toPx() + xShift
        moveTo(startX, y)
        quadraticTo(w * 0.25f + xShift, y - 8.dp.toPx(), w * 0.5f + xShift, y)
        quadraticTo(w * 0.75f + xShift, y + 8.dp.toPx(), w + 24.dp.toPx() + xShift, y)
      }
      drawPath(
        path = path,
        color = waveColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
      )
    }
  }
}

@Composable
private fun ArchipelagoPill(
  label: String,
  selected: Boolean,
  isDark: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1f,
    animationSpec = tween(durationMillis = if (isPressed) 110 else 160, easing = EaseOutExpo),
    label = "archipelagoPillScale"
  )

  val bg = when {
    selected -> LagoonTeal
    isDark -> Color(0xFF102A3A).copy(alpha = 0.92f)
    else -> Color.White.copy(alpha = 0.90f)
  }
  val textColor = when {
    selected -> Color.White
    isDark -> Color(0xFFE0F2FE)
    else -> Color(0xFF0F172A)
  }

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(999.dp),
    color = bg,
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) Color(0xFF5EEAD4) else Color(0xFF0EA5E9).copy(alpha = 0.28f)
    ),
    shadowElevation = if (selected) 4.dp else 1.dp,
    modifier = Modifier
      .defaultMinSize(minHeight = 44.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .testTag(testTag)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Text(
        text = label,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun IslandAdventureCompassCard(
  title: String,
  primaryProgressText: String,
  secondaryProgressText: String,
  gatesText: String,
  progressPercent: Int,
  ctaLabel: String,
  isArabic: Boolean,
  isDark: Boolean,
  onContinueClick: () -> Unit
) {
  val pctText = "${MemoryFlashcardManager.formatNumber(progressPercent, isArabic)}%"
  val ctaInteraction = remember { MutableInteractionSource() }
  val ctaPressed by ctaInteraction.collectIsPressedAsState()
  val ctaScale by animateFloatAsState(
    targetValue = if (ctaPressed) 0.95f else 1f,
    animationSpec = tween(durationMillis = if (ctaPressed) 110 else 160, easing = EaseOutExpo),
    label = "compassCtaScale"
  )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = if (isDark) Color(0xFF0F2D3D).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f),
    border = BorderStroke(1.2.dp, LagoonEmerald.copy(alpha = 0.45f)),
    shadowElevation = 4.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("islands_compass_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Sun-gold circular progress ring
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(58.dp)
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeW = 6.dp.toPx()
          val inset = strokeW / 2f
          val arcSize = Size(size.width - strokeW, size.height - strokeW)
          drawArc(
            color = IslandUpcomingSlate.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
          )
          drawArc(
            brush = Brush.sweepGradient(listOf(LagoonEmerald, LagoonGold, LagoonEmerald)),
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
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = if (isDark) Color(0xFF5EEAD4) else LagoonTeal,
          style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = if (isDark) Color.White else Color(0xFF0F172A),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "$primaryProgressText · $secondaryProgressText",
          style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.SemiBold,
          color = if (isDark) Color(0xFFBAE6FD) else Color(0xFF475569),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "🚩 $gatesText",
          style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.Bold,
          color = LagoonEmerald
        )
      }

      Button(
        onClick = onContinueClick,
        interactionSource = ctaInteraction,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = LagoonEmerald,
          contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 46.dp)
          .graphicsLayer {
            scaleX = ctaScale
            scaleY = ctaScale
          }
          .testTag("islands_sail_next_btn")
      ) {
        Text(
          text = "⛵ $ctaLabel",
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun ReturnArrivalRibbon(
  event: KidsIslandReturnEvent,
  nextRecommendedSurah: Int,
  isArabic: Boolean,
  res: Resources,
  onSailNext: () -> Unit,
  onDismiss: () -> Unit
) {
  val surahName = remember(event.surahNumber) {
    MinshawiJuz30Repository.getSurahNameAr(event.surahNumber)
  }
  val message = when {
    event.surahJustCompleted -> res.getString(R.string.kids_mode_return_banner_completed, surahName)
    event.starsGained > 0 -> res.getString(
      R.string.kids_mode_return_banner_stars,
      surahName,
      MemoryFlashcardManager.formatNumber(event.starsGained, isArabic)
    )
    else -> res.getString(R.string.kids_mode_return_banner_welcome, surahName)
  }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color(0xFF065F46),
    border = BorderStroke(1.5.dp, Color(0xFFFDE047)),
    shadowElevation = 8.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_return_arrival_ribbon")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        text = if (event.surahJustCompleted || event.starsGained > 0) "✨" else "⛵",
        fontSize = 20.sp
      )
      Text(
        text = message,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      if (nextRecommendedSurah != event.surahNumber || !event.surahJustCompleted) {
        Surface(
          onClick = onSailNext,
          shape = RoundedCornerShape(999.dp),
          color = Color(0xFFFACC15)
        ) {
          Text(
            text = res.getString(R.string.kids_mode_return_next_island_btn),
            color = Color(0xFF0F172A),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }
      }
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.16f))
          .clickable(onClick = onDismiss)
      ) {
        Text(
          text = "✕",
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun SurahIslandStepRow(
  node: SurahJourneyNode,
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean,
  showExplorerBoat: Boolean,
  isReturningHere: Boolean,
  returnBurstNonce: Int,
  starsGainedOnReturn: Int,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onClick: () -> Unit
) {
  val surahNumText = MemoryFlashcardManager.formatNumber(node.surahNumber, isArabic)
  val masteredText = MemoryFlashcardManager.formatNumber(node.masteredAyat, isArabic)
  val totalText = MemoryFlashcardManager.formatNumber(node.totalAyat, isArabic)
  val starsText = MemoryFlashcardManager.formatNumber(node.starsEarned, isArabic)

  val isActiveBeacon = node.status == JourneyNodeStatus.NEXT_UP || node.status == JourneyNodeStatus.IN_PROGRESS

  val islandInteraction = remember { MutableInteractionSource() }
  val islandPressed by islandInteraction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (islandPressed) 0.93f else 1f,
    animationSpec = tween(durationMillis = if (islandPressed) 110 else 170, easing = EaseOutExpo),
    label = "islandPressScale"
  )

  // Buoyant arrival bounce + expanding water splash ring when returning to this island
  val arrivalScale = remember { Animatable(1f) }
  val rippleProgress = remember { Animatable(0f) }

  LaunchedEffect(isReturningHere, returnBurstNonce) {
    if (isReturningHere && returnBurstNonce > 0) {
      arrivalScale.snapTo(0.92f)
      rippleProgress.snapTo(0f)
      launch {
        arrivalScale.animateTo(
          targetValue = 1.07f,
          animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        )
        arrivalScale.animateTo(
          targetValue = 1f,
          animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        )
      }
      launch {
        rippleProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 620, easing = EaseOutExpo)
        )
      }
    }
  }

  // Calm gentle boat bob when the Explorer Boat is docked at this island
  val boatBobDp = if (showExplorerBoat) {
    val infinite = rememberInfiniteTransition(label = "boatBob")
    val b by infinite.animateFloat(
      initialValue = -3f,
      targetValue = 3f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "boatBobY"
    )
    b
  } else {
    0f
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(118.dp)
      .testTag("island_surah_node_${node.surahNumber}")
  ) {
    val rowWidth = maxWidth
    val islandWidth = 90.dp
    val islandHeight = 76.dp
    val centerX = rowWidth * laneFraction
    val placeCardOnRight = laneFraction <= 0.52f

    // Winding Ocean Route between islands
    OceanRouteConnectorCanvas(
      laneFraction = laneFraction,
      prevLaneFraction = prevLaneFraction,
      nextLaneFraction = nextLaneFraction,
      isTopPathLit = isTopPathLit,
      isBottomPathLit = isBottomPathLit,
      isDark = isDark
    )

    // Illustrated 3D Stepping-Stone Island
    val islandOffsetX = (centerX - islandWidth / 2).coerceIn(2.dp, rowWidth - islandWidth - 2.dp)
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .offset(x = islandOffsetX, y = 20.dp)
        .size(width = islandWidth, height = islandHeight)
        .graphicsLayer {
          val totalScale = pressScale * arrivalScale.value
          scaleX = totalScale
          scaleY = totalScale
        }
        .clickable(
          interactionSource = islandInteraction,
          indication = null,
          onClick = onClick
        )
    ) {
      IllustratedIslandBaseCanvas(
        status = node.status,
        badgeTier = node.badge?.tier,
        progressFraction = node.progressFraction,
        rippleProgress = if (isReturningHere) rippleProgress.value else 0f,
        starsSparkle = isReturningHere && starsGainedOnReturn > 0,
        isDark = isDark
      )

      // Crest sitting atop the island mound
      when {
        node.badge != null -> {
          val accText = "${MemoryFlashcardManager.formatNumber(node.badge.bestAccuracyPercent, isArabic)}%"
          SurahBadgeMedallion(
            tier = node.badge.tier,
            centerLabel = accText,
            size = 54.dp,
            animateGlow = node.badge.tier == SurahBadgeTier.DIAMOND
          )
        }
        node.status == JourneyNodeStatus.COMPLETED_STARS -> {
          Surface(
            shape = CircleShape,
            color = LagoonEmerald,
            border = BorderStroke(2.5.dp, Color(0xFFFEF08A)),
            shadowElevation = 4.dp,
            modifier = Modifier
              .offset(y = (-6).dp)
              .size(44.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "✓",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }
        }
        else -> {
          Surface(
            shape = CircleShape,
            color = if (isActiveBeacon) Color(0xFF065F46) else if (isDark) Color(0xFF15384B) else Color.White,
            border = BorderStroke(
              width = if (isActiveBeacon) 2.5.dp else 1.5.dp,
              color = if (isActiveBeacon) Color(0xFFFACC15) else LagoonTeal.copy(alpha = 0.45f)
            ),
            shadowElevation = if (isActiveBeacon) 4.dp else 1.dp,
            modifier = Modifier
              .offset(y = (-6).dp)
              .size(42.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = surahNumText,
                color = if (isActiveBeacon) Color.White else if (isDark) Color(0xFFE0F2FE) else Color(0xFF0F172A),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }
          }
        }
      }
    }

    // Playful Explorer Boat floating beside the current or returned island
    if (showExplorerBoat) {
      val boatX = if (placeCardOnRight) {
        (islandOffsetX - 24.dp).coerceAtLeast(4.dp)
      } else {
        (islandOffsetX + islandWidth - 6.dp).coerceAtMost(rowWidth - 34.dp)
      }
      Surface(
        shape = CircleShape,
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.5.dp, LagoonGold),
        shadowElevation = 4.dp,
        modifier = Modifier
          .offset(x = boatX, y = (16 + boatBobDp).dp)
          .size(34.dp)
          .testTag("islands_explorer_boat")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(text = "⛵", fontSize = 17.sp)
        }
      }
    }

    // Companion Island Info Card
    val cardWidth = (rowWidth * 0.44f).coerceIn(138.dp, 214.dp)
    val cardOffsetX = if (placeCardOnRight) {
      (centerX + islandWidth / 2 + 6.dp).coerceAtMost(rowWidth - cardWidth - 4.dp)
    } else {
      (centerX - islandWidth / 2 - cardWidth - 6.dp).coerceAtLeast(4.dp)
    }

    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
      targetValue = if (cardPressed) 0.96f else 1f,
      animationSpec = tween(durationMillis = if (cardPressed) 110 else 160, easing = EaseOutExpo),
      label = "islandCardScale"
    )

    val cardBorderColor = when {
      isReturningHere -> LagoonGold
      node.badge != null -> visualStyleForTier(node.badge.tier).cardAccent.copy(alpha = 0.6f)
      node.status == JourneyNodeStatus.COMPLETED_STARS -> LagoonEmerald.copy(alpha = 0.65f)
      isActiveBeacon -> LagoonGold.copy(alpha = 0.85f)
      else -> if (isDark) Color(0xFF1E4A60) else Color.White.copy(alpha = 0.9f)
    }

    Surface(
      onClick = onClick,
      interactionSource = cardInteraction,
      shape = RoundedCornerShape(18.dp),
      color = when {
        isReturningHere && !isDark -> Color(0xFFFFFBEB)
        isReturningHere && isDark -> Color(0xFF1A3A4A)
        isDark -> Color(0xFF0E2B3C).copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.93f)
      },
      border = BorderStroke(if (isActiveBeacon || isReturningHere) 1.6.dp else 1.dp, cardBorderColor),
      shadowElevation = if (isActiveBeacon || isReturningHere) 5.dp else 1.5.dp,
      modifier = Modifier
        .offset(x = cardOffsetX, y = 18.dp)
        .width(cardWidth)
        .defaultMinSize(minHeight = 76.dp)
        .graphicsLayer {
          scaleX = cardScale
          scaleY = cardScale
        }
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
            color = if (isDark) Color.White else Color(0xFF0F172A),
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
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFFD97706),
              style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            )
          }
        }

        val subLine = when {
          node.badge != null -> if (isArabic) node.badge.tier.titleAr else node.badge.tier.titleEn
          node.status == JourneyNodeStatus.COMPLETED_STARS -> res.getString(R.string.kids_mode_island_completed_pill)
          node.status == JourneyNodeStatus.NEXT_UP -> res.getString(R.string.kids_journey_next_step_pill)
          node.status == JourneyNodeStatus.IN_PROGRESS -> res.getString(R.string.kids_journey_in_progress_pill)
          else -> res.getString(R.string.kids_mode_island_ready_pill)
        }
        Text(
          text = subLine,
          fontSize = 11.sp,
          fontWeight = if (isActiveBeacon || node.isCompleted) FontWeight.Bold else FontWeight.Medium,
          color = when {
            node.badge != null -> visualStyleForTier(node.badge.tier).cardAccent
            node.isCompleted || isActiveBeacon -> LagoonEmerald
            else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          LinearProgressIndicator(
            progress = { node.progressFraction },
            color = if (node.isCompleted) LagoonGold else LagoonEmerald,
            trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0),
            modifier = Modifier
              .weight(1f)
              .height(5.dp)
              .clip(RoundedCornerShape(999.dp))
          )
          Text(
            text = "$masteredText/$totalText",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFBAE6FD) else Color(0xFF64748B),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
          )
        }
      }
    }
  }
}

@Composable
private fun IllustratedIslandBaseCanvas(
  status: JourneyNodeStatus,
  badgeTier: SurahBadgeTier?,
  progressFraction: Float,
  rippleProgress: Float,
  starsSparkle: Boolean,
  isDark: Boolean
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val islandCenterY = h * 0.62f

    // Expanding celebratory splash ripple on return
    if (rippleProgress > 0.01f && rippleProgress < 0.99f) {
      val alpha = (1f - rippleProgress) * 0.65f
      val rX = w * (0.42f + 0.34f * rippleProgress)
      val rY = h * (0.28f + 0.24f * rippleProgress)
      drawOval(
        color = LagoonGold.copy(alpha = alpha),
        topLeft = Offset(cx - rX, islandCenterY - rY),
        size = Size(rX * 2f, rY * 2f),
        style = Stroke(width = 3.dp.toPx())
      )
    }

    // 1. Shallow turquoise water halo around the island
    drawOval(
      color = if (isDark) Color(0xFF22D3EE).copy(alpha = 0.24f) else IslandWaterShallow.copy(alpha = 0.58f),
      topLeft = Offset(w * 0.04f, h * 0.38f),
      size = Size(w * 0.92f, h * 0.54f)
    )

    // 2. Warm sandy beach rim (3D base)
    val sandBrush = Brush.verticalGradient(
      colors = listOf(IslandSandLight, IslandSandWarm),
      startY = h * 0.42f,
      endY = h * 0.88f
    )
    drawOval(
      brush = sandBrush,
      topLeft = Offset(w * 0.10f, h * 0.44f),
      size = Size(w * 0.80f, h * 0.42f)
    )

    // 3. Lush emerald / golden-crowned grassy island mound
    val topColors = when {
      badgeTier == SurahBadgeTier.DIAMOND -> listOf(Color(0xFF67E8F9), Color(0xFF0284C7))
      badgeTier == SurahBadgeTier.GOLD -> listOf(Color(0xFFFDE047), Color(0xFFD97706))
      status == JourneyNodeStatus.COMPLETED_BADGED || status == JourneyNodeStatus.COMPLETED_STARS ->
        listOf(Color(0xFF6EE7B7), IslandGrassDeep)
      status == JourneyNodeStatus.NEXT_UP || status == JourneyNodeStatus.IN_PROGRESS ->
        listOf(IslandGrassBright, LagoonTeal)
      else -> if (isDark) listOf(Color(0xFF335C6E), Color(0xFF1F3E4E)) else listOf(Color(0xFF86EFAC), Color(0xFF10B981))
    }
    drawOval(
      brush = Brush.verticalGradient(
        colors = topColors,
        startY = h * 0.34f,
        endY = h * 0.78f
      ),
      topLeft = Offset(w * 0.16f, h * 0.36f),
      size = Size(w * 0.68f, h * 0.36f)
    )

    // 4. Progress arc hugging the active island
    if (progressFraction > 0f && status != JourneyNodeStatus.COMPLETED_BADGED && status != JourneyNodeStatus.COMPLETED_STARS) {
      drawArc(
        color = LagoonGold,
        startAngle = -90f,
        sweepAngle = 360f * progressFraction.coerceIn(0.05f, 1f),
        useCenter = false,
        topLeft = Offset(w * 0.12f, h * 0.32f),
        size = Size(w * 0.76f, h * 0.48f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
      )
    }

    // 5. Floating star sparkles when returning with newly earned stars
    if (starsSparkle && rippleProgress > 0.01f && rippleProgress < 0.99f) {
      val sparkleAlpha = (1f - rippleProgress).coerceIn(0f, 1f)
      val angles = listOf(-140.0, -90.0, -40.0)
      angles.forEachIndexed { i, deg ->
        val rad = Math.toRadians(deg)
        val dist = w * (0.28f + 0.32f * rippleProgress)
        val sx = cx + (cos(rad) * dist).toFloat()
        val sy = h * 0.38f + (sin(rad) * dist).toFloat() - (rippleProgress * 12.dp.toPx())
        drawCircle(
          color = if (i == 1) Color(0xFFFDE047).copy(alpha = sparkleAlpha) else Color(0xFF34D399).copy(alpha = sparkleAlpha),
          radius = (4.5f - rippleProgress * 1.5f).dp.toPx().coerceAtLeast(1.5f),
          center = Offset(sx, sy)
        )
      }
    }
  }
}

@Composable
private fun JuzArchipelagoIslandRow(
  node: JuzOverviewNode,
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onClick: () -> Unit
) {
  val juzNumText = MemoryFlashcardManager.formatNumber(node.juzSpec.juzNumber, isArabic)
  val doneStr = MemoryFlashcardManager.formatNumber(node.completedSurahs, isArabic)
  val totalStr = MemoryFlashcardManager.formatNumber(node.totalSurahs, isArabic)
  val isActiveBeacon = node.status == JourneyNodeStatus.NEXT_UP || node.status == JourneyNodeStatus.IN_PROGRESS

  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = tween(durationMillis = if (pressed) 110 else 160, easing = EaseOutExpo),
    label = "juzIslandScale"
  )

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(114.dp)
      .testTag("island_juz_node_${node.juzSpec.juzNumber}")
  ) {
    val rowWidth = maxWidth
    val islandWidth = 90.dp
    val islandHeight = 76.dp
    val centerX = rowWidth * laneFraction
    val placeCardOnRight = laneFraction <= 0.52f

    OceanRouteConnectorCanvas(
      laneFraction = laneFraction,
      prevLaneFraction = prevLaneFraction,
      nextLaneFraction = nextLaneFraction,
      isTopPathLit = isTopPathLit,
      isBottomPathLit = isBottomPathLit,
      isDark = isDark
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .offset(x = (centerX - islandWidth / 2).coerceIn(2.dp, rowWidth - islandWidth - 2.dp), y = 18.dp)
        .size(width = islandWidth, height = islandHeight)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
      IllustratedIslandBaseCanvas(
        status = node.status,
        badgeTier = node.bestTierInJuz.takeIf { it != SurahBadgeTier.NONE },
        progressFraction = node.progressFraction,
        rippleProgress = 0f,
        starsSparkle = false,
        isDark = isDark
      )
      Surface(
        shape = CircleShape,
        color = if (node.isCompleted) LagoonEmerald else if (isActiveBeacon) LagoonSky else if (isDark) Color(0xFF15384B) else Color.White,
        border = BorderStroke(2.dp, if (isActiveBeacon || node.isCompleted) LagoonGold else LagoonTeal.copy(alpha = 0.5f)),
        shadowElevation = 3.dp,
        modifier = Modifier
          .offset(y = (-6).dp)
          .size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = juzNumText,
            color = if (node.isCompleted || isActiveBeacon) Color.White else if (isDark) Color.White else Color(0xFF0F172A),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
          )
        }
      }
    }

    val cardWidth = (rowWidth * 0.45f).coerceIn(142.dp, 218.dp)
    val cardOffsetX = if (placeCardOnRight) {
      (centerX + islandWidth / 2 + 6.dp).coerceAtMost(rowWidth - cardWidth - 4.dp)
    } else {
      (centerX - islandWidth / 2 - cardWidth - 6.dp).coerceAtLeast(4.dp)
    }

    Surface(
      onClick = onClick,
      shape = RoundedCornerShape(18.dp),
      color = if (isDark) Color(0xFF0E2B3C).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.93f),
      border = BorderStroke(
        width = if (isActiveBeacon) 1.5.dp else 1.dp,
        color = if (node.isCompleted) LagoonEmerald else if (isActiveBeacon) LagoonGold else Color(0xFF0EA5E9).copy(alpha = 0.25f)
      ),
      shadowElevation = if (isActiveBeacon) 4.dp else 1.5.dp,
      modifier = Modifier
        .offset(x = cardOffsetX, y = 16.dp)
        .width(cardWidth)
        .defaultMinSize(minHeight = 76.dp)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = "${res.getString(R.string.kids_journey_juz_label, juzNumText)} · ${node.juzSpec.nameAr}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.ExtraBold,
          color = if (isDark) Color.White else Color(0xFF0F172A),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = res.getString(R.string.kids_journey_juz_surahs_count, doneStr, totalStr),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (node.isCompleted) LagoonEmerald else if (isDark) Color(0xFFBAE6FD) else Color(0xFF64748B)
        )
        Text(
          text = "⛵ ${res.getString(R.string.kids_journey_open_juz_hint)} ${if (isArabic) "←" else "→"}",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = LagoonSky
        )
      }
    }
  }
}

@Composable
private fun IslandTreasureGateRow(
  gate: JourneyMilestoneGate,
  hasTopConnector: Boolean,
  hasBottomConnector: Boolean,
  isArabic: Boolean,
  isDark: Boolean,
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
      .testTag("island_milestone_gate_${gate.id}")
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val cx = size.width / 2f
      val color = if (gate.isUnlocked) LagoonGold else IslandUpcomingSlate.copy(alpha = 0.45f)
      val strokeW = 5.dp.toPx()
      if (hasTopConnector) {
        drawLine(color = color, start = Offset(cx, 0f), end = Offset(cx, size.height * 0.25f), strokeWidth = strokeW)
      }
      if (hasBottomConnector) {
        drawLine(color = color, start = Offset(cx, size.height * 0.75f), end = Offset(cx, size.height), strokeWidth = strokeW)
      }
    }

    Surface(
      shape = RoundedCornerShape(22.dp),
      color = when {
        gate.isUnlocked -> Color(0xFF065F46)
        isDark -> Color(0xFF102D3E).copy(alpha = 0.94f)
        else -> Color.White.copy(alpha = 0.94f)
      },
      border = BorderStroke(
        width = if (gate.isUnlocked) 2.dp else 1.dp,
        color = if (gate.isUnlocked) LagoonGold else LagoonTeal.copy(alpha = 0.35f)
      ),
      shadowElevation = if (gate.isUnlocked) 6.dp else 2.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
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
              if (gate.isUnlocked) LagoonGold.copy(alpha = 0.24f)
              else LagoonTeal.copy(alpha = 0.14f)
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
              color = if (gate.isUnlocked) Color.White else if (isDark) Color.White else Color(0xFF0F172A),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (gate.isUnlocked) LagoonGold else LagoonTeal.copy(alpha = 0.14f)
            ) {
              Text(
                text = if (gate.isUnlocked) {
                  "✓ ${res.getString(R.string.kids_journey_gate_unlocked)}"
                } else {
                  "$curStr / $reqStr"
                },
                color = if (gate.isUnlocked) Color(0xFF0F172A) else if (isDark) Color(0xFF5EEAD4) else LagoonTeal,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }
          Text(
            text = subtitle,
            color = if (gate.isUnlocked) Color.White.copy(alpha = 0.90f) else if (isDark) Color(0xFFBAE6FD) else Color(0xFF475569),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          if (!gate.isUnlocked) {
            LinearProgressIndicator(
              progress = { gate.progressFraction },
              color = LagoonEmerald,
              trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0),
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
private fun OceanRouteConnectorCanvas(
  laneFraction: Float,
  prevLaneFraction: Float?,
  nextLaneFraction: Float?,
  isTopPathLit: Boolean,
  isBottomPathLit: Boolean,
  isDark: Boolean
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val midY = h * 0.54f
    val curX = w * laneFraction
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

    fun drawSeaRoute(startX: Float, startY: Float, endX: Float, endY: Float, lit: Boolean) {
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
          color = LagoonGold.copy(alpha = 0.28f),
          style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(LagoonGold, LagoonEmerald)),
          style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
        )
      } else {
        drawPath(
          path = path,
          color = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.35f) else LagoonSky.copy(alpha = 0.32f),
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
      drawSeaRoute(topEntryX, 0f, curX, midY, isTopPathLit)
    }
    if (nextLaneFraction != null) {
      val bottomExitX = (curX + w * nextLaneFraction) / 2f
      drawSeaRoute(curX, midY, bottomExitX, h, isBottomPathLit)
    }
  }
}

@Composable
private fun IslandLaunchSheetContent(
  node: SurahJourneyNode,
  difficultWordsCount: Int,
  isArabic: Boolean,
  isDark: Boolean,
  res: Resources,
  onListenAndRepeat: () -> Unit,
  onReciteNow: () -> Unit,
  onReviewTrickyWords: () -> Unit
) {
  val surahNumStr = MemoryFlashcardManager.formatNumber(node.surahNumber, isArabic)
  val masteredStr = MemoryFlashcardManager.formatNumber(node.masteredAyat, isArabic)
  val totalStr = MemoryFlashcardManager.formatNumber(node.totalAyat, isArabic)
  val starsStr = MemoryFlashcardManager.formatNumber(node.starsEarned, isArabic)
  val maxStarsStr = MemoryFlashcardManager.formatNumber((node.totalAyat * 3).coerceAtLeast(3), isArabic)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 600.dp)
      .padding(horizontal = 20.dp, vertical = 10.dp)
      .testTag("island_launch_sheet_${node.surahNumber}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Island emblem + Surah title
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (node.badge != null) {
        val accText = "${MemoryFlashcardManager.formatNumber(node.badge.bestAccuracyPercent, isArabic)}%"
        SurahBadgeMedallion(
          tier = node.badge.tier,
          centerLabel = accText,
          size = 64.dp,
          animateGlow = node.badge.tier == SurahBadgeTier.DIAMOND
        )
      } else {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(listOf(LagoonTeal, LagoonEmerald))
            )
        ) {
          Text(
            text = "🏝️",
            fontSize = 30.sp
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = "سورة ${node.surahNameAr}",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
          color = if (isDark) Color.White else Color(0xFF0F172A),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "#$surahNumStr · ${res.getString(R.string.kids_journey_ayat_progress, masteredStr, totalStr)} · ⭐ $starsStr/$maxStarsStr",
          style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.SemiBold,
          color = if (isDark) Color(0xFFBAE6FD) else Color(0xFF475569)
        )
      }
    }

    LinearProgressIndicator(
      progress = { node.progressFraction },
      color = if (node.isCompleted) LagoonGold else LagoonEmerald,
      trackColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFE2E8F0),
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(999.dp))
    )

    // Two joyful big action buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Button(
        onClick = onListenAndRepeat,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = LagoonEmerald,
          contentColor = Color.White
        ),
        modifier = Modifier
          .weight(1.15f)
          .defaultMinSize(minHeight = 54.dp)
          .testTag("island_sheet_listen_btn")
      ) {
        Text(
          text = "🎧 ${res.getString(R.string.kids_mode_island_start_listen)}",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 14.sp,
          maxLines = 1
        )
      }

      FilledTonalButton(
        onClick = onReciteNow,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = if (isDark) Color(0xFF1E3A5F) else Color(0xFFE0F2FE),
          contentColor = if (isDark) Color(0xFF7DD3FC) else LagoonSky
        ),
        modifier = Modifier
          .weight(0.95f)
          .defaultMinSize(minHeight = 54.dp)
          .testTag("island_sheet_recite_btn")
      ) {
        Text(
          text = "🎙️ ${res.getString(R.string.kids_mode_island_start_recite)}",
          fontWeight = FontWeight.ExtraBold,
          fontSize = 14.sp,
          maxLines = 1
        )
      }
    }

    if (difficultWordsCount > 0) {
      val countStr = MemoryFlashcardManager.formatNumber(difficultWordsCount, isArabic)
      OutlinedButton(
        onClick = onReviewTrickyWords,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 48.dp)
          .testTag("island_sheet_review_words_btn")
      ) {
        Text(
          text = "📊 ${res.getString(R.string.kids_mode_island_review_words, countStr)}",
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
  }
}
