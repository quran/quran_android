package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract.StudyTab
import com.quran.labs.androidquran.hybrid.search.UthmaniFontCache
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.state.PageColor
import kotlin.math.roundToInt

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
private val REPEAT_OPTIONS = listOf(0, 1, 2, 3, -1)

@Composable
fun StudyDrawerSheet(
  uiState: HybridReaderUiState,
  elapsedMs: Long,
  progressFraction: Float,
  onSelectTab: (StudyTab) -> Unit,
  onCollapse: () -> Unit,
  onShowQariList: () -> Unit,
  onPlayPauseClick: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrev: () -> Unit,
  onSpeedCycle: (Float) -> Unit,
  onRepeatCycle: (Int) -> Unit,
  onTafsirStepAyah: (Boolean) -> Unit,
  onSelectTafsirSource: (String) -> Unit,
  onOpenTafsirManager: () -> Unit,
  modifier: Modifier = Modifier,
  onStop: () -> Unit = {},
  onCancelDownload: () -> Unit = {},
  onAllowMobileDownload: () -> Unit = {}
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val drawerShape = RoundedCornerShape(28.dp)
  val floatBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.92f)
  val sheetTopColor = quranColors.surfaceElevated
  val screenHeightDp = LocalConfiguration.current.screenHeightDp
  val activeTab = if (uiState.selectedStudyTab == StudyTab.TAFSIR) StudyTab.TAFSIR else StudyTab.RECITATION
  var isTafsirExpanded by remember { mutableStateOf(false) }
  LaunchedEffect(activeTab) {
    if (activeTab != StudyTab.TAFSIR) {
      isTafsirExpanded = false
    }
  }
  val targetHeightDp = when {
    activeTab == StudyTab.RECITATION -> {
      if (uiState.showNetworkPrompt || uiState.downloadProgress != null) 272.dp else 238.dp
    }
    isTafsirExpanded -> (screenHeightDp * 0.80f).coerceIn(440f, 680f).dp
    else -> (screenHeightDp * 0.42f).coerceIn(304f, 380f).dp
  }
  val drawerBodyHeight by animateDpAsState(
    targetValue = targetHeightDp,
    animationSpec = AdaptiveMotionTokens.drawerSheetSpring(),
    label = "studyDrawerHeight"
  )
  var dragAccumulator by remember { mutableFloatStateOf(0f) }

  // Detached Floating Apple-Style 2-Tab Study Sheet (10dp horizontal inset, 12dp + navBar bottom inset, Z4)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
      .shadow(
        elevation = if (isDark) 18.dp else 16.dp,
        shape = drawerShape,
        ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.36f else 0.17f),
        spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.54f else 0.31f)
      )
      .clip(drawerShape)
      .background(Brush.verticalGradient(listOf(sheetTopColor, quranColors.cardSurface)))
      .border(width = 1.dp, color = floatBorder, shape = drawerShape)
      .height(drawerBodyHeight)
      .padding(top = 10.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
    verticalArrangement = Arrangement.spacedBy(11.dp)
  ) {
    // Drag handle + collapse button row
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .pointerInput(onCollapse) {
          detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onDragEnd = {
              if (dragAccumulator > 64f) {
                onCollapse()
              }
              dragAccumulator = 0f
            },
            onVerticalDrag = { _, dragAmount -> dragAccumulator += dragAmount }
          )
        }
        .padding(vertical = 2.dp)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .width(38.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(99.dp))
          .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
      )
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .size(28.dp)
          .clip(CircleShape)
          .semantics(mergeDescendants = true) {}
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onCollapse
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = ReaderIcons.ChevronDown,
          contentDescription = stringResource(R.string.hybrid_reader_collapse_drawer_cd),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Recessed 2-Tab Segmented Rail: التلاوة | التفسير
    StudyDrawerTabRail(
      selectedTab = activeTab,
      onSelectTab = onSelectTab
    )

    // Lazily compose ONLY the active tab content
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      when (activeTab) {
        StudyTab.TAFSIR -> TafsirTabContent(
          tafsirState = uiState.tafsirState,
          isExpanded = isTafsirExpanded,
          onToggleExpand = { isTafsirExpanded = !isTafsirExpanded },
          onStepAyah = onTafsirStepAyah,
          onSelectSource = onSelectTafsirSource,
          onOpenTafsirManager = onOpenTafsirManager
        )

        else -> RecitationTabContent(
          uiState = uiState,
          elapsedMs = elapsedMs,
          progressFraction = progressFraction,
          onShowQariList = onShowQariList,
          onPlayPauseClick = onPlayPauseClick,
          onSkipNext = onSkipNext,
          onSkipPrev = onSkipPrev,
          onStop = onStop,
          onSpeedCycle = onSpeedCycle,
          onRepeatCycle = onRepeatCycle,
          onCancelDownload = onCancelDownload,
          onAllowMobileDownload = onAllowMobileDownload
        )
      }
    }
  }
}

@Composable
private fun StudyDrawerTabRail(
  selectedTab: StudyTab,
  onSelectTab: (StudyTab) -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val accent = MaterialTheme.colorScheme.primary
  val cardHairline = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val thumbBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.88f)
  val railShape = RoundedCornerShape(999.dp)
  val itemShape = RoundedCornerShape(999.dp)
  val trackColor = if (isDark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(railShape)
      .background(trackColor)
      .border(width = 1.dp, color = cardHairline, shape = railShape)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    StudyTab.DRAWER_TABS.forEach { tab ->
      val selected = tab == selectedTab
      val bgColor by animateColorAsState(
        targetValue = if (selected) quranColors.surfaceElevated else Color.Transparent,
        animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
        label = "studyTabBg"
      )
      val fgColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
        label = "studyTabFg"
      )
      val label = stringResource(tab.labelRes())

      Row(
        modifier = Modifier
          .weight(1f)
          .then(
            if (selected) {
              Modifier.shadow(
                elevation = if (isDark) 7.dp else 6.dp,
                shape = itemShape,
                ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.22f else 0.10f),
                spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.35f else 0.18f)
              )
            } else {
              Modifier
            }
          )
          .clip(itemShape)
          .background(bgColor)
          .then(
            if (selected) Modifier.border(width = 1.dp, color = thumbBorder, shape = itemShape)
            else Modifier
          )
          .semantics(mergeDescendants = true) { this.selected = selected }
          .clickable(role = Role.Tab, onClick = { onSelectTab(tab) })
          .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = tab.iconVector(),
          contentDescription = label,
          tint = fgColor,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = label,
          color = fgColor,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun RecitationTabContent(
  uiState: HybridReaderUiState,
  elapsedMs: Long,
  progressFraction: Float,
  onShowQariList: () -> Unit,
  onPlayPauseClick: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrev: () -> Unit,
  onStop: () -> Unit,
  onSpeedCycle: (Float) -> Unit,
  onRepeatCycle: (Int) -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val accent = MaterialTheme.colorScheme.primary
  val isDark = !quranColors.isLightToolbar
  val textPrimary = MaterialTheme.colorScheme.onBackground
  val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val fallbackAyah = uiState.firstAyahOnPage.coerceAtLeast(1)
  val targetAyah = uiState.activeStudyAyah ?: SuraAyah(uiState.suraNumber, fallbackAyah)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Qari Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = if (isDark) 0.14f else 0.11f))
            .border(
              width = 1.dp,
              color = accent.copy(alpha = if (isDark) 0.24f else 0.22f),
              shape = RoundedCornerShape(13.dp)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = ReaderIcons.Waveform,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = uiState.qariFullName,
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = stringResource(
              R.string.hybrid_reader_qari_subtitle_format,
              uiState.suraName,
              QuranUtils.getLocalizedNumber(targetAyah.ayah)
            ),
            color = textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Switcher pill: القارئ (Color 1 soft fill + Color 1 text)
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(accent.copy(alpha = if (isDark) 0.14f else 0.11f))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = onShowQariList)
          .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_reader_qari_switch),
          color = accent,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
        Icon(
          imageVector = if (isRtl) ReaderIcons.ChevronLeft else ReaderIcons.ArrowRight,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(12.dp)
        )
      }
    }

    // Scrubber + Elapsed / Total times
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      SlimProgressTrack(
        progress = progressFraction,
        trackHeight = 4.5.dp,
        thumbDiameter = 11.dp,
        trackColor = accent.copy(alpha = if (isDark) 0.20f else 0.18f),
        fillColor = accent
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = ReaderFormatters.formatDuration(elapsedMs),
          color = textSecondary,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = if (uiState.ayahDurationMs > 0L) {
            ReaderFormatters.formatDuration(uiState.ayahDurationMs)
          } else {
            val reqStart = uiState.playbackStart
            val reqEnd = uiState.playbackEnd
            if (reqStart != null && reqEnd != null) {
              "${QuranUtils.getLocalizedNumber(targetAyah.ayah)} / ${QuranUtils.getLocalizedNumber(reqEnd.ayah)}"
            } else {
              ReaderFormatters.formatDuration(0L)
            }
          },
          color = textSecondary,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    // Transport Controls Row: Speed | SkipPrev | Main Play/Pause | SkipNext | Repeat
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Speed pill
      val nextSpeed = remember(uiState.playbackSpeed) {
        val idx = PLAYBACK_SPEEDS.indexOfFirst { kotlin.math.abs(it - uiState.playbackSpeed) < 0.05f }
        if (idx >= 0) PLAYBACK_SPEEDS[(idx + 1) % PLAYBACK_SPEEDS.size] else 1.0f
      }
      val speedBg = if (kotlin.math.abs(uiState.playbackSpeed - 1.0f) > 0.05f) {
        accent.copy(alpha = if (isDark) 0.14f else 0.11f)
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      }
      Box(
        modifier = Modifier
          .height(32.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(speedBg)
          .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(999.dp))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = { onSpeedCycle(nextSpeed) })
          .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(
            R.string.hybrid_reader_speed_format,
            ReaderFormatters.formatSpeed(uiState.playbackSpeed)
          ),
          color = textPrimary,
          style = TextStyle(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            textDirection = TextDirection.Ltr
          )
        )
      }

      // Skip Prev / Main Play (Z2 C1-tinted shadow) / Skip Next / Stop (only when audio is active)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onSkipPrev),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isRtl) ReaderIcons.SkipNext else ReaderIcons.SkipPrev,
            contentDescription = stringResource(R.string.hybrid_reader_previous_ayah_cd),
            tint = textPrimary,
            modifier = Modifier.size(16.dp)
          )
        }

        Box(
          modifier = Modifier
            .size(48.dp)
            .shadow(
              elevation = if (isDark) 7.dp else 6.dp,
              shape = CircleShape,
              ambientColor = quranColors.ctaBackground.copy(alpha = if (isDark) 0.22f else 0.10f),
              spotColor = quranColors.ctaBackground.copy(alpha = if (isDark) 0.35f else 0.18f)
            )
            .clip(CircleShape)
            .background(quranColors.ctaBackground)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onPlayPauseClick),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (uiState.isPlaying) ReaderIcons.Pause else ReaderIcons.Play,
            contentDescription = stringResource(
              if (uiState.isPlaying) R.string.hybrid_reader_pause_cd else R.string.hybrid_reader_play_cd
            ),
            tint = quranColors.onCta,
            modifier = Modifier.size(18.dp)
          )
        }

        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onSkipNext),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isRtl) ReaderIcons.SkipPrev else ReaderIcons.SkipNext,
            contentDescription = stringResource(R.string.hybrid_reader_next_ayah_cd),
            tint = textPrimary,
            modifier = Modifier.size(16.dp)
          )
        }

        if (uiState.isAudioActive) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Button, onClick = onStop),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = ReaderIcons.Stop,
              contentDescription = stringResource(R.string.stop),
              tint = textPrimary,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }

      // Repeat pill
      val repeatActive = uiState.repeatCount != 0
      val nextRepeat = remember(uiState.repeatCount) {
        val idx = REPEAT_OPTIONS.indexOf(uiState.repeatCount)
        if (idx >= 0) REPEAT_OPTIONS[(idx + 1) % REPEAT_OPTIONS.size] else 1
      }
      val repeatLabel = when {
        uiState.repeatCount == 0 -> stringResource(R.string.hybrid_reader_repeat)
        uiState.repeatCount < 0 -> stringResource(R.string.hybrid_reader_repeat_infinite)
        else -> stringResource(
          R.string.hybrid_reader_repeat_count_format,
          QuranUtils.getLocalizedNumber(uiState.repeatCount)
        )
      }
      Row(
        modifier = Modifier
          .height(32.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(
            if (repeatActive) accent.copy(alpha = if (isDark) 0.14f else 0.11f)
            else MaterialTheme.colorScheme.surfaceVariant
          )
          .border(
            width = 1.dp,
            color = if (repeatActive) accent.copy(alpha = 0.28f) else quranColors.divider,
            shape = RoundedCornerShape(999.dp)
          )
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = { onRepeatCycle(nextRepeat) })
          .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = QuranIcons.Repeat,
          contentDescription = repeatLabel,
          tint = if (repeatActive) accent else textPrimary,
          modifier = Modifier.size(14.dp)
        )
        Text(
          text = repeatLabel,
          color = if (repeatActive) accent else textPrimary,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
      }
    }

    // Download / Network Prompt banner when active
    if (uiState.showNetworkPrompt || uiState.downloadProgress != null) {
      val promptText = if (uiState.showNetworkPrompt) {
        stringResource(R.string.hybrid_reader_download_mobile_prompt)
      } else {
        stringResource(R.string.hybrid_reader_downloading)
      }
      val actionText = if (uiState.showNetworkPrompt) {
        stringResource(R.string.hybrid_reader_download_allow)
      } else {
        stringResource(R.string.hybrid_reader_download_cancel)
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(12.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = promptText,
          color = textSecondary,
          fontSize = 11.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = actionText,
          color = accent,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clickable(
              role = Role.Button,
              onClick = { if (uiState.showNetworkPrompt) onAllowMobileDownload() else onCancelDownload() }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
  }
}

@Composable
private fun TafsirTabContent(
  tafsirState: TafsirDisplayState,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onStepAyah: (Boolean) -> Unit,
  onSelectSource: (String) -> Unit,
  onOpenTafsirManager: () -> Unit
) {
  val context = LocalContext.current
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val accent = MaterialTheme.colorScheme.primary
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val uthmaniFontFamily = remember(context) { UthmaniFontCache.get(context) }
  var showSourceMenu by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Header: Reference + Prev/Next Ayah Stepper + Dedicated قائمة التفاسير Pill Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        IconStepButton(
          icon = if (isRtl) ReaderIcons.ArrowRight else ReaderIcons.ChevronLeft,
          contentDescription = stringResource(R.string.hybrid_reader_previous_ayah_cd),
          onClick = { onStepAyah(false) }
        )
        Text(
          text = stringResource(
            R.string.hybrid_reader_selection_tag_format,
            tafsirState.suraName,
            QuranUtils.getLocalizedNumber(tafsirState.ayah)
          ),
          color = quranColors.editorialAccent,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        IconStepButton(
          icon = if (isRtl) ReaderIcons.ChevronLeft else ReaderIcons.ArrowRight,
          contentDescription = stringResource(R.string.hybrid_reader_next_ayah_cd),
          onClick = { onStepAyah(true) }
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      Box {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = if (isDark) 0.14f else 0.11f))
            .border(
              width = 1.dp,
              color = accent.copy(alpha = if (isDark) 0.28f else 0.22f),
              shape = RoundedCornerShape(999.dp)
            )
            .semantics(mergeDescendants = true) {}
            .clickable(
              role = Role.Button,
              onClick = {
                if (tafsirState.availableSources.isEmpty()) {
                  onOpenTafsirManager()
                } else {
                  showSourceMenu = true
                }
              }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = ReaderIcons.Index,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp)
          )
          Text(
            text = stringResource(R.string.hybrid_reader_tafsir_list),
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
        }
        DropdownMenu(
          expanded = showSourceMenu,
          onDismissRequest = { showSourceMenu = false }
        ) {
          tafsirState.availableSources.forEach { source ->
            val selected = source.filename == tafsirState.selectedFilename
            val label = if (!TafsirLookupHelper.hasArabicScript(source.displayName)) {
              TafsirLookupHelper.localizedArabicTafsirTitle(source.filename, source.displayName)
                ?: source.displayName
            } else {
              source.displayName
            }
            DropdownMenuItem(
              text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                  )
                  if (selected) {
                    Icon(
                      imageVector = ReaderIcons.Check,
                      contentDescription = null,
                      tint = accent,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
              },
              onClick = {
                showSourceMenu = false
                onSelectSource(source.filename)
              }
            )
          }
          HorizontalDivider()
          DropdownMenuItem(
            text = {
              Text(
                text = stringResource(R.string.hybrid_reader_tafsir_download_more),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent
              )
            },
            onClick = {
              showSourceMenu = false
              onOpenTafsirManager()
            }
          )
        }
      }
    }

    // Body
    when (tafsirState.loadState) {
      TafsirLoadState.EMPTY -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = ReaderIcons.Tafsir,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.hybrid_reader_tafsir_empty_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.hybrid_reader_tafsir_empty_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(12.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(999.dp))
              .background(quranColors.ctaBackground)
              .semantics(mergeDescendants = true) {}
              .clickable(role = Role.Button, onClick = onOpenTafsirManager)
              .padding(horizontal = 18.dp, vertical = 9.dp)
          ) {
            Text(
              text = stringResource(R.string.hybrid_reader_tafsir_download),
              color = quranColors.onCta,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      TafsirLoadState.LOADING, TafsirLoadState.IDLE -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            color = accent,
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
          )
        }
      }

      TafsirLoadState.LOADED -> {
        val scrollState = rememberScrollState()
        val hasOverflow = scrollState.maxValue > 0
        val showToggle = hasOverflow || isExpanded
        val chevronRotation by animateFloatAsState(
          targetValue = if (isExpanded) 180f else 0f,
          animationSpec = AdaptiveMotionTokens.panelSpring(),
          label = "tafsirExpandChevron"
        )
        val cardShape = RoundedCornerShape(16.dp)
        val cardHairline = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .shadow(
              elevation = if (isDark) 2.5.dp else 2.dp,
              shape = cardShape,
              ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.16f else 0.06f),
              spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.24f else 0.10f)
            )
            .clip(cardShape)
            .background(quranColors.surfaceElevated)
            .border(width = 1.dp, color = cardHairline, shape = cardShape)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(
                start = 14.dp,
                end = 14.dp,
                top = 12.dp,
                bottom = if (showToggle) 36.dp else 12.dp
              ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (!tafsirState.ayahArabicText.isNullOrBlank()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                  .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(14.dp))
                  .padding(horizontal = 12.dp, vertical = 10.dp)
              ) {
                Text(
                  text = tafsirState.ayahArabicText,
                  fontFamily = uthmaniFontFamily,
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 16.sp,
                  lineHeight = 28.sp
                )
              }
            }

            Text(
              text = tafsirState.fullText.ifBlank {
                stringResource(R.string.hybrid_reader_tafsir_unavailable_ayah)
              },
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 13.5.sp,
              lineHeight = 23.sp
            )
          }

          if (hasOverflow && !isExpanded) {
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(34.dp)
                .background(
                  Brush.verticalGradient(
                    listOf(Color.Transparent, quranColors.surfaceElevated)
                  )
                )
            )
          }

          if (showToggle) {
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .width(44.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(quranColors.cardSurface)
                .border(width = 1.dp, color = cardHairline, shape = RoundedCornerShape(999.dp))
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.Button, onClick = onToggleExpand),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = ReaderIcons.ChevronUp,
                contentDescription = stringResource(
                  if (isExpanded) R.string.hybrid_reader_tafsir_collapse_cd
                  else R.string.hybrid_reader_tafsir_expand_cd
                ),
                tint = accent,
                modifier = Modifier
                  .size(15.dp)
                  .rotate(chevronRotation)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun IconStepButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  Box(
    modifier = Modifier
      .size(28.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .border(width = 1.dp, color = quranColors.divider, shape = CircleShape)
      .semantics(mergeDescendants = true) {}
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.size(14.dp)
    )
  }
}

@Composable
fun ReaderAppearanceSheet(
  appearance: ReaderAppearanceState,
  onDismiss: () -> Unit,
  onSelectAppThemeStyle: (AppThemeStyle) -> Unit,
  onSelectAppThemeMode: (String) -> Unit,
  onSelectPageColor: (PageColor) -> Unit,
  onMatchAppThemeChanged: (Boolean) -> Unit,
  onNightTextBrightnessChanged: (Int) -> Unit,
  onNightBgBrightnessChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
  onOledDarkModeChanged: (Boolean) -> Unit = {}
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val accent = MaterialTheme.colorScheme.primary
  val textPrimary = MaterialTheme.colorScheme.onBackground
  val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
  val sheetShape = RoundedCornerShape(28.dp)
  val floatBorder = Color.White.copy(alpha = if (isDark) 0.13f else 0.92f)
  val screenHeightDp = LocalConfiguration.current.screenHeightDp

  Column(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
      .shadow(
        elevation = if (isDark) 18.dp else 16.dp,
        shape = sheetShape,
        ambientColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.36f else 0.17f),
        spotColor = quranColors.shadowTint.copy(alpha = if (isDark) 0.54f else 0.31f)
      )
      .clip(sheetShape)
      .background(Brush.verticalGradient(listOf(quranColors.surfaceElevated, quranColors.cardSurface)))
      .border(width = 1.dp, color = floatBorder, shape = sheetShape)
      .heightIn(max = (screenHeightDp * 0.72f).dp)
      .padding(top = 12.dp, start = 18.dp, end = 18.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Drag handle
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .width(38.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(99.dp))
        .background(textSecondary.copy(alpha = 0.35f))
    )

    // Header: Appearance + Done pill
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = stringResource(R.string.hybrid_reader_appearance_sheet_title),
        color = textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
      )
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(999.dp))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = onDismiss)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_reader_done),
          color = textPrimary,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold
        )
        Icon(
          imageVector = ReaderIcons.Close,
          contentDescription = null,
          tint = textSecondary,
          modifier = Modifier.size(13.dp)
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // 1. App Auto / Light / Dark Mode (stays open on tap!)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.hybrid_reader_appearance_app_mode),
          color = textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(12.dp))
            .padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          val modes = listOf(
            Constants.THEME_DEFAULT to R.string.hybrid_reader_mode_auto,
            Constants.THEME_LIGHT to R.string.hybrid_reader_mode_light,
            Constants.THEME_DARK to R.string.hybrid_reader_mode_dark
          )
          modes.forEach { (modeKey, labelRes) ->
            val selected = appearance.appThemeMode == modeKey
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) quranColors.ctaBackground else Color.Transparent)
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.RadioButton, onClick = { onSelectAppThemeMode(modeKey) })
                .padding(vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = stringResource(labelRes),
                color = if (selected) quranColors.onCta else textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // 1b. Pure Black OLED Dark Mode Toggle (visible ONLY in Dark Mode; keeps sheet open!)
      if (isDark) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(width = 1.dp, color = quranColors.divider, shape = RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Switch, onClick = { onOledDarkModeChanged(!appearance.isOledDarkMode) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = stringResource(R.string.hybrid_appearance_oled_title),
              color = textPrimary,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = stringResource(R.string.hybrid_appearance_oled_subtitle),
              color = textSecondary,
              fontSize = 11.sp
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Switch(checked = appearance.isOledDarkMode, onCheckedChange = null)
        }
      }

      // 2. Duo-Tone Palettes with crisp overlapping 2-dot swatches
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.hybrid_reader_appearance_palette),
          color = textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AppThemeStyle.entries.forEach { style ->
            val selected = appearance.appThemeStyle == style
            val stylePalette = if (quranColors.isLightToolbar) style.lightPalette else style.darkPalette
            val chipBg = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(chipBg)
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) accent else quranColors.divider,
                  shape = RoundedCornerShape(999.dp)
                )
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.RadioButton, onClick = { onSelectAppThemeStyle(style) })
                .padding(horizontal = 11.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              val ringColor = if (selected) quranColors.cardSurface else MaterialTheme.colorScheme.surfaceVariant
              Canvas(modifier = Modifier.width(24.dp).height(14.dp)) {
                val strokePx = 1.5.dp.toPx()
                val r = ((size.height - strokePx * 2f) * 0.5f).coerceAtLeast(1f)
                val cy = size.height * 0.5f
                val offset = r * 1.3f
                val c1x = size.width * 0.5f - offset * 0.5f
                val c2x = size.width * 0.5f + offset * 0.5f
                drawCircle(color = stylePalette.accent, radius = r, center = Offset(c1x, cy))
                drawCircle(color = ringColor, radius = r, center = Offset(c1x, cy), style = Stroke(strokePx))
                drawCircle(color = stylePalette.editorialAccent, radius = r, center = Offset(c2x, cy))
                drawCircle(color = ringColor, radius = r, center = Offset(c2x, cy), style = Stroke(strokePx))
              }
              Text(
                text = stringResource(style.labelRes()),
                color = if (selected) accent else textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // 3. Mushaf Page Appearance (Light / Sepia / Dark)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.hybrid_reader_appearance_mushaf),
          color = textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val mushafOptions = listOf(
            Triple(
              PageColor.Light,
              R.string.hybrid_reader_mushaf_light,
              Color(0xFFFAF6EE) to Color(0xFF212121)
            ),
            Triple(
              PageColor.Sepia,
              R.string.hybrid_reader_mushaf_sepia,
              Color(0xFFF3E4C8) to Color(0xFF2E2419)
            ),
            Triple(
              PageColor.Dark,
              R.string.hybrid_reader_mushaf_dark,
              Color(0xFF1A1E1C) to Color(0xFFE0E4E1)
            )
          )
          mushafOptions.forEach { (pageColor, labelRes, swatchPair) ->
            val selected = when (pageColor) {
              PageColor.Dark -> appearance.isNightPage
              PageColor.Sepia -> !appearance.isNightPage && appearance.pageTheme == PageThemeConstants.PAGE_THEME_SEPIA
              PageColor.Light -> !appearance.isNightPage && appearance.pageTheme != PageThemeConstants.PAGE_THEME_SEPIA
            }
            val cardShape = RoundedCornerShape(12.dp)
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(cardShape)
                .background(swatchPair.first)
                .border(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) accent else quranColors.divider,
                  shape = cardShape
                )
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.RadioButton, onClick = { onSelectPageColor(pageColor) })
                .padding(vertical = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = stringResource(R.string.hybrid_reader_mushaf_preview_sample),
                color = swatchPair.second,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = stringResource(labelRes),
                color = swatchPair.second,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      // 4. Match App Theme Toggle
      val matchApp = appearance.pageAppearance == PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Switch, onClick = { onMatchAppThemeChanged(!matchApp) })
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = stringResource(R.string.hybrid_reader_mushaf_match_app),
          color = textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        Switch(checked = matchApp, onCheckedChange = null)
      }

      // 5. Night Mode Brightness Sliders (only when dark Mushaf is active)
      if (appearance.isNightPage) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.hybrid_reader_night_text_brightness),
            color = textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
          Slider(
            value = appearance.textBrightness.toFloat(),
            onValueChange = { onNightTextBrightnessChanged(it.roundToInt()) },
            valueRange = PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS.toFloat()..255f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
          )
          Text(
            text = stringResource(R.string.hybrid_reader_night_bg_brightness),
            color = textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
          Slider(
            value = appearance.backgroundBrightness.toFloat(),
            onValueChange = { onNightBgBrightnessChanged(it.roundToInt()) },
            valueRange = 0f..PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS.toFloat(),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
          )
        }
      }
    }
  }
}

private fun StudyTab.labelRes(): Int = when (this) {
  StudyTab.RECITATION -> R.string.hybrid_reader_tab_recitation
  StudyTab.TAFSIR -> R.string.hybrid_reader_tab_tafsir
  StudyTab.APPEARANCE -> R.string.hybrid_reader_tab_appearance
}

private fun StudyTab.iconVector(): ImageVector = when (this) {
  StudyTab.RECITATION -> ReaderIcons.Waveform
  StudyTab.TAFSIR -> ReaderIcons.Tafsir
  StudyTab.APPEARANCE -> ReaderIcons.Appearance
}

private fun AppThemeStyle.labelRes(): Int = when (this) {
  AppThemeStyle.DEFAULT -> R.string.hybrid_reader_palette_default
  AppThemeStyle.GREEN -> R.string.hybrid_reader_palette_green
  AppThemeStyle.BLUE -> R.string.hybrid_reader_palette_blue
  AppThemeStyle.BEIGE -> R.string.hybrid_reader_palette_beige
  AppThemeStyle.GRAY -> R.string.hybrid_reader_palette_gray
}
