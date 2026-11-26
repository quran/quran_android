package com.quran.labs.androidquran.hybrid.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract.StudyTab
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.feature.pagetheme.state.PageColor
import kotlinx.coroutines.delay

@Composable
fun HybridReaderOverlay(
  controller: HybridReaderController,
  onBackToHub: () -> Unit,
  onJumpPage: () -> Unit,
  onTogglePageBookmark: () -> Unit,
  onAyahListen: (SuraAyah) -> Unit,
  onAyahTafsir: (SuraAyah) -> Unit,
  onAyahBookmark: (SuraAyah) -> Unit,
  onAyahCopy: (SuraAyah, SuraAyah) -> Unit,
  onAyahShare: (SuraAyah, SuraAyah) -> Unit,
  onPlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrev: () -> Unit,
  onSpeedChanged: (Float) -> Unit,
  onRepeatChanged: (Int) -> Unit,
  onShowQariList: () -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit,
  onOpenTafsirManager: () -> Unit,
  onTafsirStepAyah: (Boolean) -> Unit,
  onAppThemeStyleSelected: (AppThemeStyle) -> Unit,
  onAppThemeModeSelected: (String) -> Unit,
  onPageAppearanceUpdated: () -> Unit,
  onMushafNudgeChanged: (Float) -> Unit,
  onStop: () -> Unit = {}
) {
  val uiStateState = controller.uiState.collectAsState()
  val appearanceState by remember(uiStateState) {
    derivedStateOf { uiStateState.value.appearanceState }
  }
  val isDarkUi = ThemeUtil.isDarkAppearance(
    appearanceState.appThemeMode,
    isSystemInDarkTheme()
  )

  QuranTheme(
    useDarkTheme = isDarkUi,
    style = appearanceState.appThemeStyle
  ) {
    HybridReaderOverlayContent(
      uiStateState = uiStateState,
      controller = controller,
      onBackToHub = onBackToHub,
      onJumpPage = onJumpPage,
      onTogglePageBookmark = onTogglePageBookmark,
      onAyahListen = onAyahListen,
      onAyahTafsir = onAyahTafsir,
      onAyahBookmark = onAyahBookmark,
      onAyahCopy = onAyahCopy,
      onAyahShare = onAyahShare,
      onPlayPause = onPlayPause,
      onSkipNext = onSkipNext,
      onSkipPrev = onSkipPrev,
      onStop = onStop,
      onSpeedChanged = onSpeedChanged,
      onRepeatChanged = onRepeatChanged,
      onShowQariList = onShowQariList,
      onCancelDownload = onCancelDownload,
      onAllowMobileDownload = onAllowMobileDownload,
      onOpenTafsirManager = onOpenTafsirManager,
      onTafsirStepAyah = onTafsirStepAyah,
      onAppThemeStyleSelected = onAppThemeStyleSelected,
      onAppThemeModeSelected = onAppThemeModeSelected,
      onPageAppearanceUpdated = onPageAppearanceUpdated,
      onMushafNudgeChanged = onMushafNudgeChanged
    )
  }
}

@Composable
private fun HybridReaderOverlayContent(
  uiStateState: State<HybridReaderUiState>,
  controller: HybridReaderController,
  onBackToHub: () -> Unit,
  onJumpPage: () -> Unit,
  onTogglePageBookmark: () -> Unit,
  onAyahListen: (SuraAyah) -> Unit,
  onAyahTafsir: (SuraAyah) -> Unit,
  onAyahBookmark: (SuraAyah) -> Unit,
  onAyahCopy: (SuraAyah, SuraAyah) -> Unit,
  onAyahShare: (SuraAyah, SuraAyah) -> Unit,
  onPlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrev: () -> Unit,
  onStop: () -> Unit,
  onSpeedChanged: (Float) -> Unit,
  onRepeatChanged: (Int) -> Unit,
  onShowQariList: () -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit,
  onOpenTafsirManager: () -> Unit,
  onTafsirStepAyah: (Boolean) -> Unit,
  onAppThemeStyleSelected: (AppThemeStyle) -> Unit,
  onAppThemeModeSelected: (String) -> Unit,
  onPageAppearanceUpdated: () -> Unit,
  onMushafNudgeChanged: (Float) -> Unit
) {
  val density = LocalDensity.current
  val statusTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
  val navBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }

  // Narrowly scoped playback timer: only triggers when playbackStatus == PLAYING.
  val playbackStatus by remember(uiStateState) { derivedStateOf { uiStateState.value.playbackStatus } }
  val playbackAyah by remember(uiStateState) { derivedStateOf { uiStateState.value.playbackAyah } }
  val playbackSpeed by remember(uiStateState) { derivedStateOf { uiStateState.value.playbackSpeed } }
  val ayahDurationMs by remember(uiStateState) { derivedStateOf { uiStateState.value.ayahDurationMs } }
  val repeatCount by remember(uiStateState) { derivedStateOf { uiStateState.value.repeatCount } }
  val isPlaying = playbackStatus == PlaybackStatus.PLAYING

  var ayahElapsedMs by remember(playbackAyah) { mutableLongStateOf(0L) }
  LaunchedEffect(playbackStatus, playbackAyah) {
    if (playbackStatus == null || playbackStatus == PlaybackStatus.PREPARING) {
      ayahElapsedMs = 0L
    }
  }
  LaunchedEffect(isPlaying, playbackAyah, playbackSpeed, ayahDurationMs, repeatCount) {
    if (isPlaying && ayahDurationMs > 0L) {
      if (ayahElapsedMs >= ayahDurationMs && repeatCount != 0) {
        ayahElapsedMs = 0L
      }
      val tickIntervalMs = 250L
      while (isPlaying) {
        if (ayahElapsedMs >= ayahDurationMs) {
          if (repeatCount != 0) {
            ayahElapsedMs = 0L
          } else {
            break
          }
        }
        delay(tickIntervalMs)
        val step = (tickIntervalMs * playbackSpeed.coerceAtLeast(0.5f)).toLong()
        ayahElapsedMs = (ayahElapsedMs + step).coerceAtMost(ayahDurationMs)
      }
    }
  }

  val progressState = remember(uiStateState) {
    derivedStateOf {
      val s = uiStateState.value
      val pbAyah = s.playbackAyah
      val pbStart = s.playbackStart
      val pbEnd = s.playbackEnd
      when {
        s.playbackStatus == null -> 0f
        s.ayahDurationMs > 0L -> (ayahElapsedMs.toFloat() / s.ayahDurationMs.toFloat()).coerceIn(0f, 1f)
        pbAyah != null && pbStart != null && pbEnd != null -> {
          val startAyah = pbStart.ayah
          val endAyah = pbEnd.ayah
          val curAyah = pbAyah.ayah
          if (pbStart.sura == pbEnd.sura && endAyah > startAyah) {
            ((curAyah - startAyah + 1).toFloat() / (endAyah - startAyah + 1).toFloat()).coerceIn(0f, 1f)
          } else {
            0.35f
          }
        }
        else -> 0f
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val viewportWidthPx = with(density) { maxWidth.toPx() }
    val viewportHeightPx = with(density) { maxHeight.toPx() }
    val drawerHeightDp = (maxHeight.value * 0.38f).coerceIn(296f, 360f).dp
    val drawerTopPx = viewportHeightPx - with(density) { drawerHeightDp.toPx() } - navBottomPx
    val topObstacleBottomPx = statusTopPx + with(density) { (8.dp + 36.dp + 6.dp).toPx() }
    val capsuleReservePx = with(density) { (54.dp + 24.dp + 8.dp).toPx() } + navBottomPx
    val bottomObstacleTopPx = viewportHeightPx - capsuleReservePx

    MushafNudgeEffect(
      uiStateState = uiStateState,
      drawerTopPx = drawerTopPx,
      viewportHeightPx = viewportHeightPx,
      onMushafNudgeChanged = onMushafNudgeChanged
    )

    DrawerScrimSlot(
      uiStateState = uiStateState,
      onCollapse = {
        val s = uiStateState.value
        if (s.isAppearanceSheetOpen) {
          controller.closeAppearanceSheet()
        } else {
          controller.closeStudyDrawer()
        }
      }
    )

    val ayahBarPlacementState = remember(
      uiStateState,
      viewportWidthPx,
      viewportHeightPx,
      topObstacleBottomPx,
      bottomObstacleTopPx,
      density
    ) {
      derivedStateOf {
        val state = uiStateState.value
        val selectedStart = state.selectedStart
        val bounds = state.ayahOverlayBounds
        if (selectedStart != null && bounds != null && !state.isAnySheetOpen) {
          computeFloatingAyahBarPlacement(
            bounds = bounds,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            topObstacleBottomPx = topObstacleBottomPx,
            bottomObstacleTopPx = bottomObstacleTopPx,
            pillHorizontalInsetPx = with(density) { 18.dp.toPx() },
            caretHeightPx = with(density) { 7.dp.toPx() },
            pillHeightPx = with(density) { 52.dp.toPx() },
            selectionTagHeightPx = with(density) { 28.dp.toPx() },
            gapFromAyahPx = with(density) { 6.dp.toPx() },
            caretHorizontalPaddingPx = with(density) { 30.dp.toPx() }
          )
        } else {
          null
        }
      }
    }

    // 1. Frosted Floating Top Pills (only recomposes on page/sura/bookmark/chrome change)
    TopPillsSlot(
      uiStateState = uiStateState,
      ayahBarPlacementState = ayahBarPlacementState,
      onBackClick = onBackToHub,
      onJumpPageClick = onJumpPage,
      onTogglePageBookmark = onTogglePageBookmark,
      onOpenAppearance = { controller.openAppearanceSheet() },
      onOpenTafsir = { controller.openStudyDrawer(StudyTab.TAFSIR) },
      modifier = Modifier.align(Alignment.TopCenter)
    )

    // 2. Floating Pill Ayah Action Bar (ZERO obstructed sacred text!)
    AyahActionBarSlot(
      uiStateState = uiStateState,
      ayahBarPlacementState = ayahBarPlacementState,
      onAyahListen = onAyahListen,
      onAyahTafsir = onAyahTafsir,
      onAyahBookmark = onAyahBookmark,
      onAyahCopy = onAyahCopy,
      onAyahShare = onAyahShare
    )

    // 3. Bottom Frosted Audio Capsule
    AudioCapsuleSlot(
      uiStateState = uiStateState,
      ayahBarPlacementState = ayahBarPlacementState,
      elapsedMsProvider = { ayahElapsedMs },
      progressProvider = { progressState.value },
      onPlayPause = onPlayPause,
      onExpandStudyDrawer = { controller.openStudyDrawer(StudyTab.RECITATION) },
      onCancelDownload = onCancelDownload,
      onAllowMobileDownload = onAllowMobileDownload,
      modifier = Modifier.align(Alignment.BottomCenter)
    )

    // 4. Detached Floating Apple-Style 2-Tab Study Sheet (Z4: Recitation | Tafsir)
    StudyDrawerSlot(
      uiStateState = uiStateState,
      controller = controller,
      elapsedMsProvider = { ayahElapsedMs },
      progressProvider = { progressState.value },
      onShowQariList = onShowQariList,
      onPlayPause = onPlayPause,
      onSkipNext = onSkipNext,
      onSkipPrev = onSkipPrev,
      onStop = onStop,
      onSpeedChanged = onSpeedChanged,
      onRepeatChanged = onRepeatChanged,
      onCancelDownload = onCancelDownload,
      onAllowMobileDownload = onAllowMobileDownload,
      onTafsirStepAyah = onTafsirStepAyah,
      onOpenTafsirManager = onOpenTafsirManager,
      modifier = Modifier.align(Alignment.BottomCenter)
    )

    // 5. Dedicated Standalone Detached Z4 Reader Appearance Sheet
    AppearanceSheetSlot(
      uiStateState = uiStateState,
      controller = controller,
      onAppThemeStyleSelected = onAppThemeStyleSelected,
      onAppThemeModeSelected = onAppThemeModeSelected,
      onPageAppearanceUpdated = onPageAppearanceUpdated,
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}

@Composable
private fun MushafNudgeEffect(
  uiStateState: State<HybridReaderUiState>,
  drawerTopPx: Float,
  viewportHeightPx: Float,
  onMushafNudgeChanged: (Float) -> Unit
) {
  val density = LocalDensity.current
  val isStudyDrawerOpen by remember(uiStateState) { derivedStateOf { uiStateState.value.isStudyDrawerOpen } }
  val unNudgedLastLineBottom by remember(uiStateState) {
    derivedStateOf { uiStateState.value.ayahOverlayBounds?.unNudgedLastLineBottom }
  }
  val nudgePx = remember(isStudyDrawerOpen, drawerTopPx, unNudgedLastLineBottom, viewportHeightPx) {
    computeStudyNudgeTranslationY(
      isStudyDrawerOpen = isStudyDrawerOpen,
      drawerTopPx = drawerTopPx,
      selectedAyahBottomPx = unNudgedLastLineBottom,
      defaultNudgePx = with(density) { 48.dp.toPx() },
      maxNudgePx = viewportHeightPx * 0.34f
    )
  }
  LaunchedEffect(nudgePx) {
    onMushafNudgeChanged(nudgePx)
  }
}

@Composable
private fun DrawerScrimSlot(
  uiStateState: State<HybridReaderUiState>,
  onCollapse: () -> Unit
) {
  val isOpen by remember(uiStateState) { derivedStateOf { uiStateState.value.isAnySheetOpen } }
  val isDark = !LocalQuranColors.current.isLightToolbar
  if (isOpen) {
    val midFade = if (isDark) Color(0x36000000) else Color(0x1A000000)
    val bottomFade = if (isDark) Color(0x52000000) else Color(0x26000000)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent, midFade, bottomFade, bottomFade)
          )
        )
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onCollapse
        )
    )
  }
}

@Composable
private fun TopPillsSlot(
  uiStateState: State<HybridReaderUiState>,
  ayahBarPlacementState: State<AyahActionPlacement?>,
  onBackClick: () -> Unit,
  onJumpPageClick: () -> Unit,
  onTogglePageBookmark: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenTafsir: () -> Unit,
  modifier: Modifier = Modifier
) {
  val visible by remember(uiStateState, ayahBarPlacementState) {
    derivedStateOf {
      val s = uiStateState.value
      (s.isChromeVisible || s.isAnySheetOpen) &&
        ayahBarPlacementState.value?.suppressTopPills != true
    }
  }
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    exit = fadeOut(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    modifier = modifier.graphicsLayer {}
  ) {
    val suraName by remember(uiStateState) { derivedStateOf { uiStateState.value.suraName } }
    val currentPage by remember(uiStateState) { derivedStateOf { uiStateState.value.currentPage } }
    val isBookmarked by remember(uiStateState) { derivedStateOf { uiStateState.value.isCurrentPageBookmarked } }
    HybridReaderTopPills(
      suraName = suraName,
      page = currentPage,
      isPageBookmarked = isBookmarked,
      onBackClick = onBackClick,
      onJumpPageClick = onJumpPageClick,
      onTogglePageBookmark = onTogglePageBookmark,
      onOpenAppearance = onOpenAppearance,
      onOpenTafsir = onOpenTafsir
    )
  }
}

@Composable
private fun AyahActionBarSlot(
  uiStateState: State<HybridReaderUiState>,
  ayahBarPlacementState: State<AyahActionPlacement?>,
  onAyahListen: (SuraAyah) -> Unit,
  onAyahTafsir: (SuraAyah) -> Unit,
  onAyahBookmark: (SuraAyah) -> Unit,
  onAyahCopy: (SuraAyah, SuraAyah) -> Unit,
  onAyahShare: (SuraAyah, SuraAyah) -> Unit
) {
  val visible by remember(ayahBarPlacementState) {
    derivedStateOf { ayahBarPlacementState.value != null }
  }
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    exit = fadeOut(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    modifier = Modifier.graphicsLayer {}
  ) {
    val placement = ayahBarPlacementState.value
    val s = uiStateState.value
    val selectedStart = s.selectedStart
    if (selectedStart != null && placement != null) {
      val selEnd = s.selectedEnd ?: selectedStart
      FloatingAyahActionBar(
        placement = placement,
        suraName = s.selectedSuraName.ifBlank { s.suraName },
        selectedStart = selectedStart,
        selectedEnd = s.selectedEnd,
        isBookmarked = s.isSelectedAyahBookmarked,
        onListenClick = { onAyahListen(selectedStart) },
        onTafsirClick = { onAyahTafsir(selectedStart) },
        onBookmarkClick = { onAyahBookmark(selectedStart) },
        onCopyClick = { onAyahCopy(selectedStart, selEnd) },
        onShareClick = { onAyahShare(selectedStart, selEnd) }
      )
    }
  }
}

@Composable
private fun AudioCapsuleSlot(
  uiStateState: State<HybridReaderUiState>,
  ayahBarPlacementState: State<AyahActionPlacement?>,
  elapsedMsProvider: () -> Long,
  progressProvider: () -> Float,
  onPlayPause: () -> Unit,
  onExpandStudyDrawer: () -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit,
  modifier: Modifier = Modifier
) {
  val visible by remember(uiStateState, ayahBarPlacementState) {
    derivedStateOf {
      val s = uiStateState.value
      val hasUrgentPrompt = s.downloadProgress != null || s.showNetworkPrompt
      !s.isAnySheetOpen && (
        hasUrgentPrompt ||
          ((s.isChromeVisible || s.isAudioActive) && ayahBarPlacementState.value?.suppressBottomCapsule != true)
        )
    }
  }
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    exit = fadeOut(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    modifier = modifier.graphicsLayer {}
  ) {
    val s = uiStateState.value
    val activeAyah = s.activeStudyAyah ?: SuraAyah(s.suraNumber, s.firstAyahOnPage.coerceAtLeast(1))
    FrostedAudioCapsule(
      qariName = s.qariShortName,
      suraName = s.suraName,
      activeAyah = activeAyah,
      isPlaying = s.isPlaying,
      isPaused = s.playbackStatus == PlaybackStatus.PAUSED,
      isPreparing = s.isPreparing,
      elapsedMs = elapsedMsProvider(),
      progressFraction = progressProvider(),
      downloadProgress = s.downloadProgress,
      showNetworkPrompt = s.showNetworkPrompt,
      onPlayPauseClick = onPlayPause,
      onExpandStudyDrawer = onExpandStudyDrawer,
      onCancelDownload = onCancelDownload,
      onAllowMobileDownload = onAllowMobileDownload
    )
  }
}

@Composable
private fun StudyDrawerSlot(
  uiStateState: State<HybridReaderUiState>,
  controller: HybridReaderController,
  elapsedMsProvider: () -> Long,
  progressProvider: () -> Float,
  onShowQariList: () -> Unit,
  onPlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrev: () -> Unit,
  onStop: () -> Unit,
  onSpeedChanged: (Float) -> Unit,
  onRepeatChanged: (Int) -> Unit,
  onCancelDownload: () -> Unit,
  onAllowMobileDownload: () -> Unit,
  onTafsirStepAyah: (Boolean) -> Unit,
  onOpenTafsirManager: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isStudyDrawerOpen by remember(uiStateState) { derivedStateOf { uiStateState.value.isStudyDrawerOpen } }
  AnimatedVisibility(
    visible = isStudyDrawerOpen,
    enter = slideInVertically(
      animationSpec = AdaptiveMotionTokens.drawerSheetSpring(),
      initialOffsetY = { it }
    ) + fadeIn(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    exit = slideOutVertically(
      animationSpec = AdaptiveMotionTokens.drawerSheetSpring(),
      targetOffsetY = { it }
    ) + fadeOut(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    modifier = modifier.graphicsLayer {}
  ) {
    val uiState = uiStateState.value
    StudyDrawerSheet(
      uiState = uiState,
      elapsedMs = elapsedMsProvider(),
      progressFraction = progressProvider(),
      onSelectTab = controller::selectStudyTab,
      onCollapse = controller::closeStudyDrawer,
      onShowQariList = onShowQariList,
      onPlayPauseClick = onPlayPause,
      onSkipNext = onSkipNext,
      onSkipPrev = onSkipPrev,
      onSpeedCycle = { speed ->
        controller.updatePendingAudioOptions(speed = speed)
        onSpeedChanged(speed)
      },
      onRepeatCycle = { repeat ->
        controller.updatePendingAudioOptions(repeat = repeat)
        onRepeatChanged(repeat)
      },
      onCancelDownload = onCancelDownload,
      onAllowMobileDownload = onAllowMobileDownload,
      onTafsirStepAyah = onTafsirStepAyah,
      onSelectTafsirSource = controller::selectTafsirSource,
      onOpenTafsirManager = onOpenTafsirManager,
      onStop = onStop
    )
  }
}

@Composable
private fun AppearanceSheetSlot(
  uiStateState: State<HybridReaderUiState>,
  controller: HybridReaderController,
  onAppThemeStyleSelected: (AppThemeStyle) -> Unit,
  onAppThemeModeSelected: (String) -> Unit,
  onPageAppearanceUpdated: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isAppearanceOpen by remember(uiStateState) { derivedStateOf { uiStateState.value.isAppearanceSheetOpen } }
  AnimatedVisibility(
    visible = isAppearanceOpen,
    enter = slideInVertically(
      animationSpec = AdaptiveMotionTokens.drawerSheetSpring(),
      initialOffsetY = { it }
    ) + fadeIn(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    exit = slideOutVertically(
      animationSpec = AdaptiveMotionTokens.drawerSheetSpring(),
      targetOffsetY = { it }
    ) + fadeOut(animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec),
    modifier = modifier.graphicsLayer {}
  ) {
    val appearance = uiStateState.value.appearanceState
    ReaderAppearanceSheet(
      appearance = appearance,
      onDismiss = controller::closeAppearanceSheet,
      onSelectAppThemeStyle = { style ->
        controller.selectAppThemeStyle(style)
        onAppThemeStyleSelected(style)
      },
      onSelectAppThemeMode = { mode ->
        controller.selectAppThemeMode(mode)
        onAppThemeModeSelected(mode)
      },
      onSelectPageColor = { pageColor: PageColor ->
        controller.selectPageColor(pageColor)
        onPageAppearanceUpdated()
      },
      onMatchAppThemeChanged = { match ->
        controller.setMatchAppTheme(match)
        onPageAppearanceUpdated()
      },
      onNightTextBrightnessChanged = { brightness ->
        controller.setNightTextBrightness(brightness)
        onPageAppearanceUpdated()
      },
      onNightBgBrightnessChanged = { brightness ->
        controller.setNightBackgroundBrightness(brightness)
        onPageAppearanceUpdated()
      }
    )
  }
}
