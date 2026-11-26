package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.os.MessageQueue
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.NativeUthmaniTypefaceCache
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pure Native Android View Main Hub replacing Compose `HybridHubScreen`, `Crossfade`,
 * and `ModalBottomSheet`. Only the Header, Index RecyclerView, and Floating Bottom Nav
 * are instantiated on cold start; Search, Bookmarks, and Palette Sheet inflate lazily on demand.
 */
class HybridHubView(
  context: Context,
  private val scope: CoroutineScope,
  private val searchEngineProvider: () -> HybridSearchEngine,
  private val searchActionHandler: HybridSearchActionHandler,
  private val onNavTabSelected: (HybridNavTab) -> Unit,
  private val onIndexSegmentSelected: (HubIndexSegment) -> Unit,
  private val onContinueReadingClick: () -> Unit,
  private val onOpenPage: (Int) -> Unit,
  private val onOpenAyah: (page: Int, sura: Int, ayah: Int) -> Unit,
  private val onOpenSettingsClick: () -> Unit,
  private val onOpenPaletteSheet: () -> Unit,
  private val onDismissPaletteSheet: () -> Unit,
  private val onSelectThemeMode: (String) -> Unit,
  private val onSelectThemeStyle: (AppThemeStyle) -> Unit,
  private val onOpenWirdGoalDialog: () -> Unit,
  private val onClearReadingBookmark: (ReadingBookmarkType) -> Unit,
  private val onRemoveRecentPage: (Int) -> Unit,
  private val onClearAllRecents: () -> Unit,
  private val onRemoveAyahBookmark: (sura: Int, ayah: Int) -> Unit,
  private val onClearHighlight: (sura: Int, ayah: Int) -> Unit,
  private val onDownloadArabicSearchDb: () -> Unit,
  private val onOpenTranslationManager: () -> Unit,
  initialPalette: HybridViewPalette = HybridViewPalette.from(AppThemeStyle.BEIGE, false),
  private val lifecycleOwner: LifecycleOwner? = (context as? LifecycleOwner)
) : FrameLayout(context) {

  private var currentHeaderHeightPx: Int = 0
  private var lastHeaderMeasureKey: Long = -1L

  private val headerOverlay = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    clipChildren = false
    clipToPadding = false
  }
  private val headerContainer = LinearLayout(context)
  private val headerTitle = TextView(context)
  private val headerActionPill = LinearLayout(context)
  private val paletteIconDrawable: VectorPathDrawable
  private val paletteButton: ImageView
  private val settingsIconDrawable: VectorPathDrawable
  private val settingsButton: ImageView

  private val contentHost = object : FrameLayout(context) {
    override fun hasOverlappingRendering(): Boolean = false
  }
  private val mainContainer = object : FrameLayout(context) {
    override fun hasOverlappingRendering(): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val wSize = MeasureSpec.getSize(widthMeasureSpec)
      val measureKey = (wSize.toLong() shl 32) or (statusBarTopPx.toLong() and 0xFFFFFFFFL)
      if (wSize > 0 && (lastHeaderMeasureKey != measureKey || headerOverlay.isLayoutRequested)) {
        val headerWSpec = MeasureSpec.makeMeasureSpec(wSize, MeasureSpec.EXACTLY)
        val headerHSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        headerOverlay.measure(headerWSpec, headerHSpec)
        lastHeaderMeasureKey = measureKey
        val h = headerOverlay.measuredHeight
        if (h > 0 && h != currentHeaderHeightPx) {
          currentHeaderHeightPx = h
          applyContentHeaderTopPadding(h)
        }
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }
  val indexRecyclerView: RecyclerView
  private val indexAdapter: HybridIndexAdapter

  private var searchView: NativeHybridSearchView? = null
  private var bookmarksView: HybridBookmarksView? = null
  private var paletteSheetView: HybridPaletteSheetView? = null

  private val bottomFadeView = View(context).apply {
    isClickable = false
    isFocusable = false
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
  }
  val bottomNavView = FloatingBottomNavView(context)

  private var palette = initialPalette
  private var latestUiState: HybridHubUiState = HybridHubUiState()
  private var lastRenderedTab: HybridNavTab? = null
  private var statusBarTopPx: Int = 0
  private var navBarBottomPx: Int = 0
  private var imeBottomPx: Int = 0

  private var cachedSegmentBar: HybridIndexRow.SegmentBar? = null
  private var cachedSurasRef: List<SuraIndexItem>? = null
  private var cachedSurahRows: List<HybridIndexRow.SurahRow> = emptyList()
  private var cachedJuzsRef: List<JuzIndexItem>? = null
  private var cachedJuzRows: List<HybridIndexRow.JuzRow> = emptyList()
  private var cachedHizbsRef: List<HizbIndexItem>? = null
  private var cachedHizbRows: List<HybridIndexRow.HizbRow> = emptyList()
  private var lastSubmittedWird: DailyWirdState? = null
  private var lastSubmittedFallbackSura: String? = null
  private var lastSubmittedSegment: HubIndexSegment? = null
  private var lastSubmittedListRef: Any? = null

  private var idlePrebuildScheduled = false
  private var hasCompletedInitialPrebuild = false
  private var hasReleasedOnStop = false
  private var isSearchViewBound = false
  private val idlePrebuildHandler = MessageQueue.IdleHandler {
    if (!isAttachedToWindow) return@IdleHandler false
    hasCompletedInitialPrebuild = true
    scope.launch(Dispatchers.IO) {
      NativeUthmaniTypefaceCache.get(context.applicationContext)
    }
    if (latestUiState.selectedTab != HybridNavTab.SEARCH) {
      val sv = ensureSearchViewShell()
      premeasureHiddenTab(sv)
    }
    if (latestUiState.selectedTab != HybridNavTab.BOOKMARKS) {
      val bv = ensureBookmarksViewShell()
      premeasureHiddenTab(bv)
    }
    false
  }
  private val idlePrebuildDelayRunnable = Runnable {
    if (isAttachedToWindow) {
      Looper.myQueue().addIdleHandler(idlePrebuildHandler)
    }
  }

  init {
    id = R.id.hybrid_hub_root
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipChildren = false
    clipToPadding = false

    paletteIconDrawable = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.APPEARANCE_FILLED,
      palette.editorialAccent,
      HybridViewTokens.dp(context, 17f)
    )
    settingsIconDrawable = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.SETTINGS,
      palette.textSecondary,
      HybridViewTokens.dp(context, 17f)
    )

    headerTitle.apply {
      text = context.getString(R.string.hybrid_app_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }

    val chipBtnSize = HybridViewTokens.dp(context, 32f)
    paletteButton = ImageView(context).apply {
      tag = "hub_palette_button"
      setImageDrawable(paletteIconDrawable)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_hub_palette_desc)
      isClickable = true
      isFocusable = true
      setOnClickListener { onOpenPaletteSheet() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    settingsButton = ImageView(context).apply {
      setImageDrawable(settingsIconDrawable)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_hub_settings_desc)
      isClickable = true
      isFocusable = true
      setOnClickListener { onOpenSettingsClick() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }

    headerActionPill.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val padH = HybridViewTokens.dp(context, 5f)
      val padV = HybridViewTokens.dp(context, 4f)
      setPadding(padH, padV, padH, padV)
      addView(paletteButton, LinearLayout.LayoutParams(chipBtnSize, chipBtnSize))
      addView(
        settingsButton,
        LinearLayout.LayoutParams(chipBtnSize, chipBtnSize).apply {
          marginStart = HybridViewTokens.dp(context, 4f)
        }
      )
    }

    headerContainer.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val hPad = HybridViewTokens.dp(context, 16f)
      val tPad = HybridViewTokens.dp(context, 10f)
      val bPad = HybridViewTokens.dp(context, 10f)
      setPadding(hPad, tPad, hPad, bPad)

      addView(
        headerTitle,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      )
      addView(
        headerActionPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
    }

    indexAdapter = HybridIndexAdapter(
      onContinueReading = onContinueReadingClick,
      onOpenWirdGoal = onOpenWirdGoalDialog,
      onSegmentSelected = onIndexSegmentSelected,
      onOpenPage = onOpenPage
    )

    contentHost.clipChildren = false
    contentHost.clipToPadding = false

    indexRecyclerView = object : RecyclerView(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }.apply {
      id = R.id.hybrid_hub_list
      layoutManager = LinearLayoutManager(context)
      adapter = indexAdapter
      clipChildren = false
      clipToPadding = false
      itemAnimator = null
      setItemViewCacheSize(4)
      val hPad = HybridViewTokens.dp(context, 16f)
      val tPad = HybridViewTokens.dp(context, 12f)
      setPadding(hPad, tPad, hPad, HybridViewTokens.dp(context, 96f))
    }

    contentHost.addView(
      indexRecyclerView,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )

    headerOverlay.apply {
      addView(
        headerContainer,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
    }

    mainContainer.apply {
      clipChildren = false
      clipToPadding = false
      addView(
        contentHost,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      )
      addView(
        headerOverlay,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP)
      )
    }

    bottomNavView.setOnTabSelectedListener(onNavTabSelected)

    addView(
      mainContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
    addView(
      bottomFadeView,
      LayoutParams(
        LayoutParams.MATCH_PARENT,
        HybridViewTokens.dp(context, 96f),
        Gravity.BOTTOM
      )
    )
    addView(
      bottomNavView,
      LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
      )
    )

    applyPalette(palette)
  }

  fun applyWindowInsets(statusTop: Int, navBottom: Int, imeBottom: Int) {
    statusBarTopPx = statusTop
    navBarBottomPx = navBottom
    imeBottomPx = imeBottom

    val hPad = HybridViewTokens.dp(context, 16f)
    val tPad = HybridViewTokens.dp(context, 10f)
    val bPad = HybridViewTokens.dp(context, 10f)
    headerContainer.setPadding(hPad, statusTop + tPad, hPad, bPad)
    searchView?.setStatusBarTopInset(statusTop)

    setPadding(0, 0, 0, imeBottom)

    val imeVisible = imeBottom > 0
    if (imeVisible) {
      bottomFadeView.visibility = View.GONE
      bottomNavView.visibility = View.GONE
      searchView?.setBottomNavOverlapPadding(HybridViewTokens.dp(context, 12f))
    } else {
      bottomFadeView.visibility = View.VISIBLE
      bottomNavView.visibility = View.VISIBLE
      bottomNavView.setPadding(
        HybridViewTokens.dp(context, 14f),
        HybridViewTokens.dp(context, 2f),
        HybridViewTokens.dp(context, 14f),
        navBottom + HybridViewTokens.dp(context, 12f)
      )
      val navOverlap = navBottom + HybridViewTokens.dp(context, 96f)
      (bottomFadeView.layoutParams as? LayoutParams)?.let { lp ->
        if (lp.height != navOverlap) {
          lp.height = navOverlap
          bottomFadeView.layoutParams = lp
        }
      }
      applyContentHeaderTopPadding(currentHeaderHeightPx)
      searchView?.setBottomNavOverlapPadding(navOverlap)
      bookmarksView?.setBottomNavOverlapPadding(navOverlap)
    }

    if (paletteSheetView?.visibility == View.VISIBLE) {
      paletteSheetView?.update(
        palette = palette,
        themeMode = latestUiState.appTheme,
        themeStyle = latestUiState.themeStyle,
        navBottomInsetPx = navBottom
      )
    }
  }

  private fun applyContentHeaderTopPadding(headerHeightPx: Int) {
    val hPad = HybridViewTokens.dp(context, 16f)
    val topPad = headerHeightPx + HybridViewTokens.dp(context, 12f)
    val navOverlap = navBarBottomPx + HybridViewTokens.dp(context, 92f)
    val bottomPad = navOverlap + HybridViewTokens.dp(context, 12f)
    val wasIndexAtTop = !indexRecyclerView.canScrollVertically(-1)
    if (indexRecyclerView.paddingLeft != hPad ||
      indexRecyclerView.paddingTop != topPad ||
      indexRecyclerView.paddingRight != hPad ||
      indexRecyclerView.paddingBottom != bottomPad
    ) {
      indexRecyclerView.setPadding(hPad, topPad, hPad, bottomPad)
      if (wasIndexAtTop) {
        (indexRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
      }
    }
    bookmarksView?.setHeaderTopPadding(headerHeightPx)
  }

  fun applyPalette(newPalette: HybridViewPalette) {
    palette = newPalette
    background = HybridViewTokens.canvasAmbientGlowDrawable(
      surface = newPalette.surface,
      surfaceTopGlow = newPalette.surfaceTopGlow
    )

    // Soft top-glow fade behind floating header so scrolled content fades cleanly without a flush hairline bar
    headerContainer.background = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(
        newPalette.surfaceTopGlow,
        ColorUtils.setAlphaComponent(newPalette.surfaceTopGlow, 232),
        ColorUtils.setAlphaComponent(newPalette.surfaceTopGlow, 0)
      )
    )
    headerTitle.setTextColor(newPalette.textPrimary)

    val strokePx = HybridViewTokens.dp(context, 1f)
    val pillRadius = HybridViewTokens.dpF(context, 999f)
    headerActionPill.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.glassFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = pillRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      view = headerActionPill,
      zLevel = 2,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = pillRadius
    )

    paletteIconDrawable.setTintColor(newPalette.editorialAccent)
    paletteButton.background = HybridViewTokens.lightweightPressCircleDrawable(
      fillColor = newPalette.editorialSoftBg,
      pressColor = newPalette.ripple
    )

    settingsIconDrawable.setTintColor(newPalette.textSecondary)
    settingsButton.background = HybridViewTokens.lightweightPressCircleDrawable(
      fillColor = Color.TRANSPARENT,
      pressColor = newPalette.ripple
    )

    bottomFadeView.background = HybridViewTokens.scrollUnderVeilDrawable(newPalette.surface)
    bottomNavView.applyPalette(newPalette)

    indexAdapter.updatePalette(newPalette)
    searchView?.applyPalette(newPalette)
    bookmarksView?.applyPalette(newPalette)
    paletteSheetView?.update(
      palette = newPalette,
      themeMode = latestUiState.appTheme,
      themeStyle = latestUiState.themeStyle,
      navBottomInsetPx = navBarBottomPx
    )
  }

  fun render(uiState: HybridHubUiState, showPaletteSheet: Boolean) {
    val tabChanged = lastRenderedTab != null && lastRenderedTab != uiState.selectedTab
    lastRenderedTab = uiState.selectedTab
    latestUiState = uiState
    bottomNavView.setSelectedTab(uiState.selectedTab)

    val activeContent: View = when (uiState.selectedTab) {
      HybridNavTab.SEARCH -> {
        if (headerOverlay.visibility != View.INVISIBLE) {
          headerOverlay.visibility = View.INVISIBLE
          headerOverlay.isClickable = false
          paletteButton.isClickable = false
          settingsButton.isClickable = false
          headerOverlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val sv = ensureSearchView()
        sv.restoreInputIfNeeded()
        sv.setStatusBarTopInset(statusBarTopPx)
        sv
      }

      HybridNavTab.BOOKMARKS -> {
        if (headerOverlay.visibility != View.VISIBLE) {
          headerOverlay.visibility = View.VISIBLE
          headerOverlay.isClickable = true
          paletteButton.isClickable = true
          settingsButton.isClickable = true
          headerOverlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        searchView?.clearInputFocusAndKeyboard()
        val bv = ensureBookmarksView()
        bv.render(uiState)
        bv
      }

      else -> {
        if (headerOverlay.visibility != View.VISIBLE) {
          headerOverlay.visibility = View.VISIBLE
          headerOverlay.isClickable = true
          paletteButton.isClickable = true
          settingsButton.isClickable = true
          headerOverlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        searchView?.clearInputFocusAndKeyboard()
        val activeListRef: Any = when (uiState.indexSegment) {
          HubIndexSegment.SURAS -> uiState.suras
          HubIndexSegment.JUZ -> uiState.juzs
          HubIndexSegment.HIZB -> uiState.hizbs
        }
        val suraName = uiState.dailyWird.lastSuraName.ifBlank { uiState.fallbackLastSuraName }
        if (lastSubmittedSegment != uiState.indexSegment ||
          lastSubmittedListRef !== activeListRef ||
          lastSubmittedWird != uiState.dailyWird ||
          lastSubmittedFallbackSura != suraName
        ) {
          lastSubmittedSegment = uiState.indexSegment
          lastSubmittedListRef = activeListRef
          lastSubmittedWird = uiState.dailyWird
          lastSubmittedFallbackSura = suraName
          indexAdapter.submitRows(buildIndexRows(uiState), uiState.indexSegment)
        }
        indexRecyclerView
      }
    }
    setActiveContentVisibility(activeContent)

    if (tabChanged &&
      isAttachedToWindow &&
      isLaidOut &&
      AdaptiveMotionTokens.areAnimationsEnabled(context)
    ) {
      activeContent.animate().cancel()
      activeContent.alpha = 1f
      activeContent.translationY = HybridViewTokens.dpF(context, 6f)
      activeContent.animate()
        .translationY(0f)
        .setDuration(AdaptiveMotionTokens.TAB_SWITCH_DURATION_MS.toLong())
        .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
        .start()
    } else if (!tabChanged) {
      activeContent.alpha = 1f
      activeContent.translationY = 0f
    }

    if (showPaletteSheet) {
      val sheet = ensurePaletteSheetView()
      sheet.update(
        palette = palette,
        themeMode = uiState.appTheme,
        themeStyle = uiState.themeStyle,
        navBottomInsetPx = navBarBottomPx
      )
      sheet.setSheetVisible(true)
    } else {
      paletteSheetView?.setSheetVisible(false)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    scheduleIdlePrebuildIfNeeded()
  }

  fun scheduleIdlePrebuildIfNeeded() {
    if (isAttachedToWindow && !idlePrebuildScheduled) {
      idlePrebuildScheduled = true
      val delayMs = if (hasCompletedInitialPrebuild || hasReleasedOnStop) {
        WARM_IDLE_PREBUILD_DELAY_MS
      } else {
        IDLE_PREBUILD_DELAY_MS
      }
      postDelayed(idlePrebuildDelayRunnable, delayMs)
    }
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(idlePrebuildDelayRunnable)
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    idlePrebuildScheduled = false
    HybridViewTokens.resetApplePressFeedback(paletteButton)
    HybridViewTokens.resetApplePressFeedback(settingsButton)
    indexRecyclerView.animate().cancel()
    indexRecyclerView.alpha = 1f
    indexRecyclerView.translationY = 0f
    searchView?.let {
      it.animate().cancel()
      it.alpha = 1f
      it.translationY = 0f
    }
    bookmarksView?.let {
      it.animate().cancel()
      it.alpha = 1f
      it.translationY = 0f
    }
    super.onDetachedFromWindow()
  }

  fun onActivityResume() {
    searchView?.restoreInputIfNeeded()
  }

  fun releaseTransientOnStop() {
    removeCallbacks(idlePrebuildDelayRunnable)
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    idlePrebuildScheduled = false
    hasReleasedOnStop = true

    searchView?.let { sv ->
      if (latestUiState.selectedTab != HybridNavTab.SEARCH) {
        sv.releaseFocusAndInput()
        sv.unbind()
        sv.removeAllViews()
        contentHost.removeView(sv)
        searchView = null
        isSearchViewBound = false
      } else {
        sv.clearInputFocusAndKeyboard()
      }
    }
    bookmarksView?.let { bv ->
      if (latestUiState.selectedTab != HybridNavTab.BOOKMARKS) {
        bv.unbind()
        bv.removeAllViews()
        contentHost.removeView(bv)
        bookmarksView = null
      }
    }
  }

  fun unbind() {
    removeCallbacks(idlePrebuildDelayRunnable)
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    idlePrebuildScheduled = false
    bottomNavView.unbind()
    searchView?.let { sv ->
      sv.unbind()
      sv.removeAllViews()
    }
    searchView = null
    isSearchViewBound = false
    bookmarksView?.unbind()
    bookmarksView = null
    indexRecyclerView.adapter = null
    indexRecyclerView.recycledViewPool.clear()
    removeAllViews()
  }

  private fun setActiveContentVisibility(active: View) {
    val children = arrayOf<View?>(indexRecyclerView, searchView, bookmarksView)
    for (child in children) {
      if (child == null) continue
      if (child === active) {
        if (child.visibility != View.VISIBLE) {
          child.visibility = View.VISIBLE
        }
        if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
          child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (child is ViewGroup && child.descendantFocusability != ViewGroup.FOCUS_AFTER_DESCENDANTS) {
          child.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
      } else {
        child.animate().cancel()
        child.alpha = 1f
        child.translationY = 0f
        if (child.visibility != View.INVISIBLE) {
          child.visibility = View.INVISIBLE
        }
        if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
          child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (child is ViewGroup && child.descendantFocusability != ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
          child.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
      }
    }
  }

  private fun premeasureHiddenTab(view: View) {
    val hostW = contentHost.width
    val hostH = contentHost.height
    if (hostW > 0 && hostH > 0) {
      view.measure(
        MeasureSpec.makeMeasureSpec(hostW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.EXACTLY)
      )
      view.layout(0, 0, hostW, hostH)
    }
  }

  private fun ensureSearchViewShell(): NativeHybridSearchView {
    searchView?.let { return it }
    return NativeHybridSearchView(context).apply {
      applyPalette(palette)
      setStatusBarTopInset(statusBarTopPx)
      setBottomNavOverlapPadding(navBarBottomPx + HybridViewTokens.dp(context, 92f))
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }.also { created ->
      searchView = created
      contentHost.addView(
        created,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      )
    }
  }

  private fun ensureSearchView(): NativeHybridSearchView {
    val sv = ensureSearchViewShell()
    if (!isSearchViewBound) {
      isSearchViewBound = true
      val searchEngine = searchEngineProvider()
      sv.onDownloadArabicSearchDb = this@HybridHubView.onDownloadArabicSearchDb
      sv.onOpenTranslationManager = this@HybridHubView.onOpenTranslationManager
      sv.bind(searchEngine, searchActionHandler, scope, lifecycleOwner)
      sv.render(searchEngine.uiState.value)
    }
    return sv
  }

  private fun ensureBookmarksViewShell(): HybridBookmarksView {
    bookmarksView?.let { return it }
    return HybridBookmarksView(
      context = context,
      onOpenPage = onOpenPage,
      onOpenAyah = onOpenAyah,
      onClearReadingBookmark = onClearReadingBookmark,
      onRemoveRecentPage = onRemoveRecentPage,
      onClearAllRecents = onClearAllRecents,
      onRemoveAyahBookmark = onRemoveAyahBookmark,
      onClearHighlight = onClearHighlight
    ).apply {
      applyPalette(palette)
      setHeaderTopPadding(currentHeaderHeightPx)
      setBottomNavOverlapPadding(navBarBottomPx + HybridViewTokens.dp(context, 92f))
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }.also { created ->
      bookmarksView = created
      contentHost.addView(
        created,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      )
    }
  }

  private fun ensureBookmarksView(): HybridBookmarksView = ensureBookmarksViewShell()

  internal fun hasPrebuiltSearchShellForTest(): Boolean = searchView != null
  internal fun hasBoundSearchEngineForTest(): Boolean = isSearchViewBound
  internal fun hasPrebuiltBookmarksShellForTest(): Boolean = bookmarksView != null
  internal fun searchViewForTest(): NativeHybridSearchView? = searchView
  internal fun paletteSheetViewForTest(): HybridPaletteSheetView? = paletteSheetView
  internal fun headerActionPillForTest(): View = headerActionPill
  internal fun bottomFadeViewForTest(): View = bottomFadeView

  private fun ensurePaletteSheetView(): HybridPaletteSheetView {
    paletteSheetView?.let { return it }
    return HybridPaletteSheetView(
      context = context,
      onSelectThemeMode = onSelectThemeMode,
      onSelectThemeStyle = onSelectThemeStyle,
      onDismiss = onDismissPaletteSheet
    ).also { created ->
      created.id = R.id.hybrid_palette_sheet_root
      paletteSheetView = created
      addView(
        created,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      )
    }
  }

  private fun buildIndexRows(uiState: HybridHubUiState): List<HybridIndexRow> {
    val rows = ArrayList<HybridIndexRow>(120)
    val suraName = uiState.dailyWird.lastSuraName.ifBlank { uiState.fallbackLastSuraName }
    rows.add(
      HybridIndexRow.Hero(
        dailyWird = uiState.dailyWird,
        lastSuraName = suraName,
        lastJuz = uiState.dailyWird.lastJuz
      )
    )
    val segRow = cachedSegmentBar?.takeIf { it.selectedSegment == uiState.indexSegment }
      ?: HybridIndexRow.SegmentBar(uiState.indexSegment).also { cachedSegmentBar = it }
    rows.add(segRow)
    when (uiState.indexSegment) {
      HubIndexSegment.SURAS -> {
        if (cachedSurasRef !== uiState.suras) {
          cachedSurasRef = uiState.suras
          cachedSurahRows = uiState.suras.map { HybridIndexRow.SurahRow(it) }
        }
        rows.addAll(cachedSurahRows)
      }
      HubIndexSegment.JUZ -> {
        if (cachedJuzsRef !== uiState.juzs) {
          cachedJuzsRef = uiState.juzs
          cachedJuzRows = uiState.juzs.map { HybridIndexRow.JuzRow(it) }
        }
        rows.addAll(cachedJuzRows)
      }
      HubIndexSegment.HIZB -> {
        if (cachedHizbsRef !== uiState.hizbs) {
          cachedHizbsRef = uiState.hizbs
          cachedHizbRows = uiState.hizbs.map { HybridIndexRow.HizbRow(it) }
        }
        rows.addAll(cachedHizbRows)
      }
    }
    return rows
  }

  private companion object {
    private const val IDLE_PREBUILD_DELAY_MS = 1500L
    private const val WARM_IDLE_PREBUILD_DELAY_MS = 250L
  }
}
