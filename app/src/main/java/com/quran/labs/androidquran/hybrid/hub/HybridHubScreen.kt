package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.FloatingBottomNav
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.HybridSearchHost
import com.quran.labs.androidquran.util.ThemeUtil

@Composable
internal fun HybridHubScreen(
  uiState: HybridHubUiState,
  hybridSearchHostProvider: () -> HybridSearchHost,
  searchActionHandler: HybridSearchActionHandler,
  showPaletteSheet: Boolean,
  showWirdGoalDialog: Boolean,
  onNavTabSelected: (HybridNavTab) -> Unit,
  onIndexSegmentSelected: (HubIndexSegment) -> Unit,
  onContinueReadingClick: () -> Unit,
  onOpenPage: (Int) -> Unit,
  onOpenAyah: (page: Int, sura: Int, ayah: Int) -> Unit,
  onOpenSettingsClick: () -> Unit,
  onOpenPaletteSheet: () -> Unit,
  onDismissPaletteSheet: () -> Unit,
  onSelectThemeMode: (String) -> Unit,
  onSelectThemeStyle: (AppThemeStyle) -> Unit,
  onOpenWirdGoalDialog: () -> Unit,
  onDismissWirdGoalDialog: () -> Unit,
  onSaveWirdGoal: (Int) -> Unit,
  onClearReadingBookmark: (ReadingBookmarkType) -> Unit,
  onRemoveRecentPage: (Int) -> Unit,
  onClearAllRecents: () -> Unit,
  onRemoveAyahBookmark: (sura: Int, ayah: Int) -> Unit,
  onClearHighlight: (sura: Int, ayah: Int) -> Unit
) {
  val isDarkActive = ThemeUtil.isDarkAppearance(uiState.appTheme, isSystemInDarkTheme())

  QuranTheme(
    useDarkTheme = isDarkActive,
    style = uiState.themeStyle
  ) {
    val quranColors = LocalQuranColors.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val ambientCanvasBrush = remember(quranColors.surfaceTopGlow, backgroundColor) {
      Brush.verticalGradient(
        colorStops = arrayOf(
          0.0f to quranColors.surfaceTopGlow,
          0.42f to backgroundColor,
          1.0f to backgroundColor
        )
      )
    }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = 98.dp + navBottomInset

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(ambientCanvasBrush)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .testTag("hub_root")
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
      ) {
        HybridHubHeader(
          onPaletteClick = onOpenPaletteSheet,
          onSettingsClick = onOpenSettingsClick
        )

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          Crossfade(
            targetState = uiState.selectedTab,
            animationSpec = AdaptiveMotionTokens.QuickFadeFloatSpec,
            label = "hubTabContent",
            modifier = Modifier.graphicsLayer {}
          ) { tab ->
            when (tab) {
              HybridNavTab.INDEX, HybridNavTab.READ -> {
                HybridIndexTabContent(
                  dailyWird = uiState.dailyWird,
                  fallbackSuraName = uiState.fallbackLastSuraName,
                  selectedSegment = uiState.indexSegment,
                  suras = uiState.suras,
                  juzs = uiState.juzs,
                  hizbs = uiState.hizbs,
                  bottomContentPadding = bottomContentPadding,
                  onContinueClick = onContinueReadingClick,
                  onEditGoalClick = onOpenWirdGoalDialog,
                  onSegmentSelected = onIndexSegmentSelected,
                  onPageSelected = onOpenPage
                )
              }

              HybridNavTab.SEARCH -> {
                val searchHost = remember(hybridSearchHostProvider) { hybridSearchHostProvider() }
                searchHost.HybridSearchScreen(
                  initialQuery = null,
                  actionHandler = searchActionHandler,
                  bottomContentPadding = bottomContentPadding,
                  onBackOrClose = { onNavTabSelected(HybridNavTab.INDEX) },
                  modifier = Modifier.fillMaxSize()
                )
              }

              HybridNavTab.BOOKMARKS -> {
                HybridBookmarksTabContent(
                  state = uiState.bookmarks,
                  bottomContentPadding = bottomContentPadding,
                  onOpenPage = onOpenPage,
                  onOpenAyah = onOpenAyah,
                  onClearReadingBookmark = onClearReadingBookmark,
                  onRemoveRecentPage = onRemoveRecentPage,
                  onClearAllRecents = onClearAllRecents,
                  onRemoveAyahBookmark = onRemoveAyahBookmark,
                  onClearHighlight = onClearHighlight
                )
              }
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(96.dp + navBottomInset)
          .background(
            Brush.verticalGradient(
              colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.45f to backgroundColor.copy(alpha = 0.72f),
                1.0f to backgroundColor
              )
            )
          )
      )

      FloatingBottomNav(
        selectedTab = uiState.selectedTab,
        onTabSelected = onNavTabSelected,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .testTag("hub_floating_bottom_nav")
      )

      if (showWirdGoalDialog) {
        DailyWirdGoalDialog(
          currentGoalPages = uiState.dailyWird.goalPages,
          onSave = onSaveWirdGoal,
          onDismiss = onDismissWirdGoalDialog
        )
      }

      if (showPaletteSheet) {
        HybridPaletteSheet(
          currentAppTheme = uiState.appTheme,
          currentThemeStyle = uiState.themeStyle,
          isDarkActive = isDarkActive,
          onSelectThemeMode = onSelectThemeMode,
          onSelectThemeStyle = onSelectThemeStyle,
          onDismiss = onDismissPaletteSheet
        )
      }
    }
  }
}
