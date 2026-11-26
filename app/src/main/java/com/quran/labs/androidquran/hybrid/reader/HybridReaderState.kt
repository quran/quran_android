package com.quran.labs.androidquran.hybrid.reader

import android.content.Intent
import androidx.compose.runtime.Immutable
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.SelectionIndicator
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
data class AyahOverlayBounds(
  val firstLineLeft: Float,
  val firstLineTop: Float,
  val firstLineRight: Float,
  val firstLineBottom: Float,
  val lastLineLeft: Float,
  val lastLineTop: Float,
  val lastLineRight: Float,
  val lastLineBottom: Float,
  val unNudgedLastLineBottom: Float = lastLineBottom
)

/**
 * Guards against uninitialized, degenerate `(0,0,0,0)`, NaN/Infinite, or completely off-screen
 * bounds before anchoring the Floating Ayah Action Bar. Prevents RTL top-right corner placement.
 */
fun AyahOverlayBounds.isValidForOverlay(
  minBottomPx: Float = 0f,
  maxTopPx: Float = Float.POSITIVE_INFINITY
): Boolean {
  if (!firstLineLeft.isFinite() || !firstLineTop.isFinite() ||
    !firstLineRight.isFinite() || !firstLineBottom.isFinite() ||
    !lastLineLeft.isFinite() || !lastLineTop.isFinite() ||
    !lastLineRight.isFinite() || !lastLineBottom.isFinite()
  ) {
    return false
  }
  val firstWidth = firstLineRight - firstLineLeft
  val lastWidth = lastLineRight - lastLineLeft
  val firstHeight = firstLineBottom - firstLineTop
  val lastHeight = lastLineBottom - lastLineTop
  val totalTop = min(firstLineTop, lastLineTop)
  val totalBottom = max(firstLineBottom, lastLineBottom)
  return firstWidth > 4f &&
    lastWidth > 4f &&
    firstHeight > 2f &&
    lastHeight > 2f &&
    totalBottom > totalTop + 4f &&
    totalBottom > minBottomPx &&
    totalTop < maxTopPx
}

enum class AyahBarPlacementDirection {
  BELOW_AYAH,
  ABOVE_AYAH
}

@Immutable
data class AyahActionPlacement(
  val direction: AyahBarPlacementDirection,
  val topPx: Float,
  val caretCenterXPx: Float,
  val pillLeftPx: Float,
  val pillWidthPx: Float,
  val showSelectionTag: Boolean = true,
  val suppressBottomCapsule: Boolean = false,
  val suppressTopPills: Boolean = false
)

enum class TafsirLoadState {
  IDLE,
  LOADING,
  LOADED,
  EMPTY
}

@Immutable
data class TafsirSourceItem(
  val filename: String,
  val displayName: String,
  val isArabic: Boolean
)

@Immutable
data class TafsirDisplayState(
  val loadState: TafsirLoadState = TafsirLoadState.IDLE,
  val sura: Int = 1,
  val ayah: Int = 1,
  val suraName: String = "",
  val ayahArabicText: String? = null,
  val sourceName: String = "",
  val selectedFilename: String? = null,
  val availableSources: List<TafsirSourceItem> = emptyList(),
  val snippetText: String = "",
  val fullText: String = ""
)

@Immutable
data class ReaderAppearanceState(
  val appThemeStyle: AppThemeStyle = AppThemeStyle.DEFAULT,
  val appThemeMode: String = Constants.THEME_DEFAULT,
  val isOledDarkMode: Boolean = false,
  val pageTheme: String = PageThemeConstants.PAGE_THEME_LIGHT,
  val pageAppearance: String = PageThemeConstants.PAGE_APPEARANCE_LIGHT,
  val isNightPage: Boolean = false,
  val textBrightness: Int = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS,
  val backgroundBrightness: Int = Constants.DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS
)

@Immutable
data class HybridReaderUiState(
  val currentPage: Int = 1,
  val suraNumber: Int = 1,
  val suraName: String = "",
  val juzNumber: Int = 1,
  val firstAyahOnPage: Int = 1,
  val isChromeVisible: Boolean = true,
  val isCurrentPageBookmarked: Boolean = false,
  val selectedStart: SuraAyah? = null,
  val selectedEnd: SuraAyah? = null,
  val selectedSuraName: String = "",
  val isSelectedAyahBookmarked: Boolean = false,
  val ayahOverlayBounds: AyahOverlayBounds? = null,
  val isStudyDrawerOpen: Boolean = false,
  val isAppearanceSheetOpen: Boolean = false,
  val selectedStudyTab: HybridReaderContract.StudyTab = HybridReaderContract.StudyTab.RECITATION,
  val qariShortName: String = "",
  val qariFullName: String = "",
  val playbackStatus: PlaybackStatus? = null,
  val playbackAyah: SuraAyah? = null,
  val playbackStart: SuraAyah? = null,
  val playbackEnd: SuraAyah? = null,
  val playbackSpeed: Float = 1.0f,
  val repeatCount: Int = 0,
  val isGaplessAudio: Boolean = false,
  val ayahDurationMs: Long = 0L,
  val downloadProgress: Int? = null,
  val showNetworkPrompt: Boolean = false,
  val tafsirState: TafsirDisplayState = TafsirDisplayState(),
  val appearanceState: ReaderAppearanceState = ReaderAppearanceState()
) {
  val isPlaying: Boolean
    get() = playbackStatus == PlaybackStatus.PLAYING

  val isPreparing: Boolean
    get() = playbackStatus == PlaybackStatus.PREPARING

  val isAudioActive: Boolean
    get() = playbackStatus != null

  val isAnySheetOpen: Boolean
    get() = isStudyDrawerOpen || isAppearanceSheetOpen

  val activeStudyAyah: SuraAyah?
    get() = selectedStart ?: playbackAyah
}

data class ParsedReaderLaunchExtras(
  val page: Int,
  val hasExplicitPage: Boolean,
  val highlightSura: Int?,
  val highlightAyah: Int?,
  val initialStudyTab: HybridReaderContract.StudyTab?,
  val autoPlayAyah: Boolean,
  val jumpToTranslation: Boolean
)

object HybridReaderIntentParser {
  fun parse(intent: Intent?, quranInfo: QuranInfo, fallbackPage: Int = Constants.PAGES_FIRST): ParsedReaderLaunchExtras {
    val extras = intent?.extras
      ?: return ParsedReaderLaunchExtras(
        page = fallbackPage,
        hasExplicitPage = false,
        highlightSura = null,
        highlightAyah = null,
        initialStudyTab = null,
        autoPlayAyah = false,
        jumpToTranslation = false
      )

    val sura = extras.getInt(HybridReaderContract.EXTRA_HIGHLIGHT_SURA, -1).takeIf { it > 0 }
    val ayah = extras.getInt(HybridReaderContract.EXTRA_HIGHLIGHT_AYAH, -1).takeIf { it > 0 }
    val hasExplicitPage = extras.containsKey("page")
    val resolvedPage = when {
      hasExplicitPage -> extras.getInt("page", fallbackPage)
      sura != null && ayah != null -> {
        val mapped = quranInfo.getPageFromSuraAyah(sura, ayah)
        if (quranInfo.isValidPage(mapped)) mapped else fallbackPage
      }
      else -> fallbackPage
    }

    val studyTab = HybridReaderContract.StudyTab.fromExtra(
      extras.getString(HybridReaderContract.EXTRA_INITIAL_STUDY_TAB)
    )
    val autoPlay = extras.getBoolean(HybridReaderContract.EXTRA_AUTO_PLAY_AYAH, false)
    val jumpToTranslation = extras.getBoolean("jumpToTranslation", false)

    return ParsedReaderLaunchExtras(
      page = resolvedPage,
      hasExplicitPage = hasExplicitPage,
      highlightSura = sura,
      highlightAyah = ayah,
      initialStudyTab = studyTab,
      autoPlayAyah = autoPlay,
      jumpToTranslation = jumpToTranslation
    )
  }
}

private data class AyahBarCandidate(
  val direction: AyahBarPlacementDirection,
  val topPx: Float,
  val showSelectionTag: Boolean,
  val suppressBottomCapsule: Boolean,
  val suppressTopPills: Boolean,
  val clearancePx: Float
)

fun computeSelectedItemOverlayBounds(
  indicator: SelectionIndicator.SelectedItemPosition,
  overlayLocationInWindow: IntArray,
  imageViewLocationInWindow: IntArray?,
  imageViewWidth: Int,
  viewPagerTranslationY: Float
): AyahOverlayBounds {
  val dx: Float
  val dy: Float
  if (imageViewLocationInWindow != null) {
    val xSideFix = if (indicator.firstItem.left > imageViewWidth && imageViewWidth > 0) {
      -imageViewWidth.toFloat()
    } else {
      0f
    }
    dx = (imageViewLocationInWindow[0] - overlayLocationInWindow[0]).toFloat() + indicator.xScroll + xSideFix
    dy = (imageViewLocationInWindow[1] - overlayLocationInWindow[1]).toFloat() + indicator.yScroll
  } else {
    // Compose line-by-line pages populate SelectedItemPosition in window coordinates.
    dx = indicator.xScroll - overlayLocationInWindow[0].toFloat()
    dy = indicator.yScroll - overlayLocationInWindow[1].toFloat()
  }
  val first = indicator.firstItem
  val last = indicator.lastItem
  val lastBottom = last.bottom + dy
  return AyahOverlayBounds(
    firstLineLeft = first.left + dx,
    firstLineTop = first.top + dy,
    firstLineRight = first.right + dx,
    firstLineBottom = first.bottom + dy,
    lastLineLeft = last.left + dx,
    lastLineTop = last.top + dy,
    lastLineRight = last.right + dx,
    lastLineBottom = lastBottom,
    unNudgedLastLineBottom = lastBottom - viewPagerTranslationY
  )
}

/**
 * Pure geometry solver for the Proposal 5 Floating Pill Ayah Action Bar.
 *
 * Guarantees zero obstructed sacred text and zero offscreen clipping across single-line,
 * multi-line, and full-page ayahs (e.g., Al-Baqarah 2:282):
 * 1. Below ayah above `bottomObstacleTopPx` (`totalBlockHeightPx`, then `compactHeightPx`).
 * 2. Above ayah below `topObstacleBottomPx` (`totalBlockHeightPx`, then `compactHeightPx`).
 * 3. Reclaims bottom capsule and/or top pill margins for tall ayahs, picking the non-overlapping,
 *    onscreen placement with maximum clearance.
 * 4. Caret center tracks the horizontal midpoint of the anchor ayah line, clamped inside
 *    the rounded pill corners.
 */
fun computeFloatingAyahBarPlacement(
  bounds: AyahOverlayBounds,
  viewportWidthPx: Float,
  viewportHeightPx: Float,
  topObstacleBottomPx: Float,
  bottomObstacleTopPx: Float,
  pillHorizontalInsetPx: Float,
  caretHeightPx: Float,
  pillHeightPx: Float,
  selectionTagHeightPx: Float,
  gapFromAyahPx: Float,
  caretHorizontalPaddingPx: Float
): AyahActionPlacement {
  val pillWidthPx = max(1f, viewportWidthPx - 2f * pillHorizontalInsetPx)
  val totalBlockHeightPx = caretHeightPx + pillHeightPx + selectionTagHeightPx
  val compactHeightPx = caretHeightPx + pillHeightPx

  val ayahTop = min(bounds.firstLineTop, bounds.lastLineTop)
  val ayahBottom = max(bounds.firstLineBottom, bounds.lastLineBottom)

  fun tryBelow(
    showSelectionTag: Boolean,
    suppressTopPills: Boolean,
    suppressBottomCapsule: Boolean,
    ayahGapPx: Float,
    edgeGapPx: Float
  ): AyahBarCandidate? {
    val renderedHeight = if (showSelectionTag) totalBlockHeightPx else compactHeightPx
    val minTop = if (suppressTopPills) max(0f, edgeGapPx) else max(0f, topObstacleBottomPx)
    val maxBottom = if (suppressBottomCapsule) {
      min(viewportHeightPx, (viewportHeightPx - edgeGapPx).coerceAtLeast(0f))
    } else {
      min(viewportHeightPx, bottomObstacleTopPx)
    }
    val topPx = max(ayahBottom + ayahGapPx, minTop)
    val bottomPx = topPx + renderedHeight
    if (topPx < 0f || bottomPx > maxBottom || bottomPx > viewportHeightPx) return null
    if (topPx < ayahBottom) return null
    val clearance = min(maxBottom - bottomPx, topPx - ayahBottom)
    return AyahBarCandidate(
      direction = AyahBarPlacementDirection.BELOW_AYAH,
      topPx = topPx,
      showSelectionTag = showSelectionTag,
      suppressBottomCapsule = suppressBottomCapsule,
      suppressTopPills = suppressTopPills,
      clearancePx = clearance
    )
  }

  fun tryAbove(
    showSelectionTag: Boolean,
    suppressTopPills: Boolean,
    suppressBottomCapsule: Boolean,
    ayahGapPx: Float,
    edgeGapPx: Float
  ): AyahBarCandidate? {
    val renderedHeight = if (showSelectionTag) totalBlockHeightPx else compactHeightPx
    val minTop = if (suppressTopPills) max(0f, edgeGapPx) else max(0f, topObstacleBottomPx)
    val maxBottom = if (suppressBottomCapsule) {
      min(viewportHeightPx, (viewportHeightPx - edgeGapPx).coerceAtLeast(0f))
    } else {
      min(viewportHeightPx, bottomObstacleTopPx)
    }
    val maxAllowedTop = min(ayahTop - ayahGapPx, maxBottom) - renderedHeight
    if (maxAllowedTop < minTop || maxAllowedTop < 0f) return null
    val bottomPx = maxAllowedTop + renderedHeight
    if (bottomPx > ayahTop || bottomPx > viewportHeightPx) return null
    val clearance = min(maxAllowedTop - minTop, ayahTop - bottomPx)
    return AyahBarCandidate(
      direction = AyahBarPlacementDirection.ABOVE_AYAH,
      topPx = maxAllowedTop,
      showSelectionTag = showSelectionTag,
      suppressBottomCapsule = suppressBottomCapsule,
      suppressTopPills = suppressTopPills,
      clearancePx = clearance
    )
  }

  val chosen: AyahBarCandidate =
    // Priority 1: Below ayah above bottomObstacleTopPx (totalBlockHeightPx, then compactHeightPx)
    tryBelow(
      showSelectionTag = true,
      suppressTopPills = false,
      suppressBottomCapsule = false,
      ayahGapPx = gapFromAyahPx,
      edgeGapPx = gapFromAyahPx
    )
      ?: tryBelow(
        showSelectionTag = false,
        suppressTopPills = false,
        suppressBottomCapsule = false,
        ayahGapPx = gapFromAyahPx,
        edgeGapPx = gapFromAyahPx
      )
      // Priority 2: Above ayah below topObstacleBottomPx (totalBlockHeightPx, then compactHeightPx)
      ?: tryAbove(
        showSelectionTag = true,
        suppressTopPills = false,
        suppressBottomCapsule = false,
        ayahGapPx = gapFromAyahPx,
        edgeGapPx = gapFromAyahPx
      )
      ?: tryAbove(
        showSelectionTag = false,
        suppressTopPills = false,
        suppressBottomCapsule = false,
        ayahGapPx = gapFromAyahPx,
        edgeGapPx = gapFromAyahPx
      )
      // Priority 3: Reclaim bottom capsule and/or top pills for tall/full-page ayahs
      ?: listOfNotNull(
        tryBelow(showSelectionTag = true, suppressTopPills = false, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
          ?: tryBelow(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx),
        tryAbove(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = false, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
          ?: tryAbove(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
      ).maxByOrNull { it.clearancePx }
      ?: listOfNotNull(
        tryBelow(showSelectionTag = false, suppressTopPills = false, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
          ?: tryBelow(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx),
        tryAbove(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = false, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
          ?: tryAbove(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = gapFromAyahPx, edgeGapPx = gapFromAyahPx)
      ).maxByOrNull { it.clearancePx }
      // Priority 3b: Same non-overlapping zero-clipping check with 0px gap margin if tight
      ?: listOfNotNull(
        tryBelow(showSelectionTag = true, suppressTopPills = false, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f)
          ?: tryBelow(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f),
        tryAbove(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = false, ayahGapPx = 0f, edgeGapPx = 0f)
          ?: tryAbove(showSelectionTag = true, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f)
      ).maxByOrNull { it.clearancePx }
      ?: listOfNotNull(
        tryBelow(showSelectionTag = false, suppressTopPills = false, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f)
          ?: tryBelow(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f),
        tryAbove(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = false, ayahGapPx = 0f, edgeGapPx = 0f)
          ?: tryAbove(showSelectionTag = false, suppressTopPills = true, suppressBottomCapsule = true, ayahGapPx = 0f, edgeGapPx = 0f)
      ).maxByOrNull { it.clearancePx }
      // Last-resort onscreen clamp (never clips offscreen; only reached when total margin < compactHeightPx)
      ?: run {
        val spaceBelow = viewportHeightPx - ayahBottom
        val spaceAbove = ayahTop
        if (spaceBelow >= spaceAbove) {
          AyahBarCandidate(
            direction = AyahBarPlacementDirection.BELOW_AYAH,
            topPx = (viewportHeightPx - compactHeightPx).coerceAtLeast(0f),
            showSelectionTag = false,
            suppressBottomCapsule = true,
            suppressTopPills = false,
            clearancePx = spaceBelow - compactHeightPx
          )
        } else {
          AyahBarCandidate(
            direction = AyahBarPlacementDirection.ABOVE_AYAH,
            topPx = 0f,
            showSelectionTag = false,
            suppressBottomCapsule = false,
            suppressTopPills = true,
            clearancePx = spaceAbove - compactHeightPx
          )
        }
      }

  val anchorCenterX = if (chosen.direction == AyahBarPlacementDirection.BELOW_AYAH) {
    (bounds.lastLineLeft + bounds.lastLineRight) * 0.5f
  } else {
    (bounds.firstLineLeft + bounds.firstLineRight) * 0.5f
  }

  val minCaretX = pillHorizontalInsetPx + caretHorizontalPaddingPx
  val maxCaretX = pillHorizontalInsetPx + pillWidthPx - caretHorizontalPaddingPx
  val caretCenterXPx = if (maxCaretX >= minCaretX) {
    anchorCenterX.coerceIn(minCaretX, maxCaretX)
  } else {
    viewportWidthPx * 0.5f
  }

  return AyahActionPlacement(
    direction = chosen.direction,
    topPx = chosen.topPx,
    caretCenterXPx = caretCenterXPx,
    pillLeftPx = pillHorizontalInsetPx,
    pillWidthPx = pillWidthPx,
    showSelectionTag = chosen.showSelectionTag,
    suppressBottomCapsule = chosen.suppressBottomCapsule,
    suppressTopPills = chosen.suppressTopPills
  )
}

/**
 * Computes negative Y translation for the Mushaf ViewPager when the Unified Study Drawer is open
 * so upper/selected ayahs sit comfortably above the drawer sheet.
 */
fun computeStudyNudgeTranslationY(
  isStudyDrawerOpen: Boolean,
  drawerTopPx: Float,
  selectedAyahBottomPx: Float?,
  defaultNudgePx: Float,
  maxNudgePx: Float
): Float {
  if (!isStudyDrawerOpen) return 0f
  if (selectedAyahBottomPx == null) {
    return -defaultNudgePx.coerceIn(0f, maxNudgePx)
  }
  val neededShift = selectedAyahBottomPx - drawerTopPx + (defaultNudgePx * 0.35f)
  val targetShift = max(defaultNudgePx, neededShift)
  return -targetShift.coerceIn(0f, maxNudgePx)
}

object ReaderFormatters {
  fun formatDuration(ms: Long): String {
    val safeSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    val minStr = QuranUtils.getLocalizedNumber(minutes)
    val secTens = QuranUtils.getLocalizedNumber(seconds / 10)
    val secOnes = QuranUtils.getLocalizedNumber(seconds % 10)
    return "$minStr:$secTens$secOnes"
  }

  fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100f).roundToInt()
    val whole = rounded / 100
    val frac = rounded % 100
    val wholeStr = QuranUtils.getLocalizedNumber(whole)
    val decimalSep = if (java.util.Locale.getDefault().language == "ar") "٫" else "."
    return when {
      frac == 0 -> "${wholeStr}${decimalSep}${QuranUtils.getLocalizedNumber(0)}"
      frac % 10 == 0 -> "${wholeStr}${decimalSep}${QuranUtils.getLocalizedNumber(frac / 10)}"
      else -> {
        val tens = QuranUtils.getLocalizedNumber(frac / 10)
        val ones = QuranUtils.getLocalizedNumber(frac % 10)
        "${wholeStr}${decimalSep}${tens}${ones}"
      }
    }
  }
}
