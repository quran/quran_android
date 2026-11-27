package com.quran.labs.androidquran.hybrid.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.state.PageColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure Native Android View overlay replacing Compose `HybridReaderOverlay` in `PagerActivity`.
 * Zero Compose composition overhead, zero per-frame `graphicsLayer` texture churn, and zero
 * idle progress redraws when audio is paused or stopped.
 */
class HybridReaderOverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val topPillsRow = LinearLayout(context)
  private val navPill = LinearLayout(context)
  private val backIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.ARROW_RIGHT, Color.BLACK, dp(15f))
  private val backButton = ImageView(context)
  private val navDivider = View(context)
  private val suraPageJumpArea = LinearLayout(context)
  private val suraTitleText = TextView(context)
  private val navPageDivider = View(context)
  private val pageNumberText = TextView(context)

  private val toolsPill = LinearLayout(context)
  private val toolSaveOutlineIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.BOOKMARK, Color.BLACK, dp(14f))
  private val toolSaveFilledIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.BOOKMARK_FILLED, Color.BLACK, dp(14f))
  private val toolSaveIconView = ImageView(context)
  private var lastRenderedPageBookmarked: Boolean? = null
  private val toolSaveText = TextView(context)
  private val toolSaveBtn = LinearLayout(context)
  private val toolDivider1 = View(context)
  private val toolAppearanceIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.APPEARANCE, Color.BLACK, dp(14f))
  private val toolAppearanceText = TextView(context)
  private val toolAppearanceBtn = LinearLayout(context)

  // Floating Ayah Action Bar (lazy-built on first ayah selection)
  private var isAyahBarBuilt = false
  private var lastAyahBarDirection: AyahBarPlacementDirection? = null
  private var lastMeasuredTopPillsBottom = -1
  private var lastMeasuredAudioCapsuleTop = -1
  private var lastFitCacheKey: String? = null
  private var lastFitMode: Int = -1
  private var appliedFitMode: Int = -1
  private var lastAppliedMaxBarW: Int = -1
  private lateinit var ayahBarWrapper: LinearLayout
  private lateinit var ayahSelectionTagPill: TextView
  private lateinit var ayahActionPill: LinearLayout
  private lateinit var ayahCaretTop: CaretView
  private lateinit var ayahCaretBottom: CaretView
  private val ayahActionIcons = ArrayList<VectorPathDrawable>(5)
  private val ayahActionTexts = ArrayList<TextView>(5)
  private val ayahActionButtons = ArrayList<LinearLayout>(5)
  private val ayahActionButtonBgs = ArrayList<HybridViewTokens.LightweightPressColorDrawable>(5)
  private var ayahBookmarkImageView: ImageView? = null
  private var ayahBookmarkOutlineIcon: VectorPathDrawable? = null
  private var ayahBookmarkFilledIcon: VectorPathDrawable? = null
  private var ayahBookmarkLabelView: TextView? = null
  private var lastRenderedAyahBookmarked: Boolean? = null

  // Frosted Audio Capsule
  private val audioCapsuleOuter = FrameLayout(context)
  private val audioCapsuleCard = LinearLayout(context)
  private val capsulePlayIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PLAY, Color.WHITE, dp(16f))
  private val capsulePauseIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PAUSE, Color.WHITE, dp(16f))
  private val capsulePlayBtn = ImageView(context)
  private var lastRenderedCapsulePlaying: Boolean? = null
  private val capsuleQariTitle = TextView(context)
  private val capsuleMetaText = TextView(context)
  private val capsuleDownloadRow = LinearLayout(context)
  private val capsuleDownloadText = TextView(context)
  private val capsuleDownloadAction = TextView(context)
  private val capsuleExpandIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHEVRON_UP, Color.BLACK, dp(16f))
  private val capsuleExpandBtn = ImageView(context)
  private val capsuleProgressTrack = View(context)
  private val capsuleProgressFill = View(context)

  // Study Drawer (lazy-built on first expand)
  private var isStudyDrawerBuilt = false
  private var isStudyDrawerRenderVisible = false
  private var isStudyDrawerOpening = false
  private var studyDrawerTargetOpen: Boolean? = null
  private var studyDrawerAnimGen: Int = 0
  private var activeDrawerTabView: View? = null
  private lateinit var studyDrawerScrim: View
  private lateinit var studyDrawerSheet: LinearLayout
  private lateinit var drawerHandle: View
  private lateinit var drawerCollapseIcon: VectorPathDrawable
  private lateinit var drawerCollapseBtn: ImageView
  private lateinit var drawerTabRail: LinearLayout
  private lateinit var studyDrawerTabHost: FrameLayout
  private val drawerTabButtons = LinkedHashMap<HybridReaderContract.StudyTab, TextView>()
  private val drawerTabThumbDrawables = LinkedHashMap<HybridReaderContract.StudyTab, LayerDrawable>()
  private val drawerTabPressDrawables = LinkedHashMap<HybridReaderContract.StudyTab, HybridViewTokens.LightweightPressColorDrawable>()
  private var lastRenderedDrawerTab: HybridReaderContract.StudyTab? = null
  private var lastRenderedDrawerPalette: HybridViewPalette? = null

  // Drawer Tab 1: Recitation (lazy-built when Recitation tab is shown)
  private var isRecitationTabBuilt = false
  private lateinit var recitationTabRoot: ScrollView
  private lateinit var recQariNameText: TextView
  private lateinit var recQariSubtitleText: TextView
  private lateinit var recQariSwitchBtn: TextView
  private lateinit var recDownloadRow: LinearLayout
  private lateinit var recDownloadText: TextView
  private lateinit var recDownloadAction: TextView
  private lateinit var recPrevIcon: VectorPathDrawable
  private lateinit var recPrevBtn: ImageView
  private lateinit var recPlayIcon: VectorPathDrawable
  private lateinit var recPauseIcon: VectorPathDrawable
  private lateinit var recPlayBtn: ImageView
  private var lastRenderedRecPlaying: Boolean? = null
  private lateinit var recNextIcon: VectorPathDrawable
  private lateinit var recNextBtn: ImageView
  private lateinit var recStopIcon: VectorPathDrawable
  private lateinit var recStopBtn: ImageView
  private lateinit var recSpeedBtn: TextView
  private lateinit var recRepeatBtn: TextView

  // Drawer Tab 2: Tafsir (lazy-built when Tafsir tab is opened)
  private var isTafsirTabBuilt = false
  private lateinit var tafsirTabRoot: LinearLayout
  private lateinit var tafsirAyahTitle: TextView
  private lateinit var tafsirListBtn: LinearLayout
  private lateinit var tafsirListIcon: VectorPathDrawable
  private lateinit var tafsirListText: TextView
  private lateinit var tafsirPrevIcon: VectorPathDrawable
  private lateinit var tafsirNextIcon: VectorPathDrawable
  private lateinit var tafsirPrevBtn: ImageView
  private lateinit var tafsirNextBtn: ImageView
  private lateinit var tafsirSourceScroll: HorizontalScrollView
  private lateinit var tafsirSourceRow: LinearLayout
  private lateinit var tafsirBodyCard: FrameLayout
  private lateinit var tafsirBodyScroll: ScrollView
  private lateinit var tafsirBodyText: TextView
  private lateinit var tafsirOverflowFade: View
  private lateinit var tafsirExpandBtn: FrameLayout
  private lateinit var tafsirExpandIcon: VectorPathDrawable
  private lateinit var tafsirExpandIconView: ImageView
  private lateinit var tafsirEmptyCard: LinearLayout
  private lateinit var tafsirEmptyTitle: TextView
  private lateinit var tafsirEmptyBody: TextView
  private lateinit var tafsirEmptyCta: Button
  private var isTafsirExpanded = false
  private var tafsirHasOverflow = false
  private var studyDrawerHeightAnimator: ValueAnimator? = null
  private val tafsirOverflowCheckRunnable = Runnable {
    if (!isAttachedToWindow || !isTafsirTabBuilt || tafsirBodyCard.visibility != View.VISIBLE) {
      return@Runnable
    }
    val scrollH = tafsirBodyScroll.height
    val contentH = tafsirBodyText.height + tafsirBodyScroll.paddingTop + tafsirBodyScroll.paddingBottom
    val overflows = (scrollH > 0 && contentH > scrollH + dp(2f)) ||
      tafsirBodyScroll.canScrollVertically(1) ||
      tafsirBodyScroll.canScrollVertically(-1)
    updateTafsirOverflowControls(overflows)
  }
  private data class TafsirChipHolder(
    val filename: String,
    val label: String,
    val view: TextView,
    val bgDrawable: GradientDrawable
  )
  private val tafsirChips = ArrayList<TafsirChipHolder>()
  private var lastTafsirSelectedFilename: String? = null
  private var lastTafsirChipPalette: HybridViewPalette? = null

  // Detached Z4 Tafsir Picker Sheet (lazy-built when user taps قائمة التفاسير)
  private var isTafsirPickerBuilt = false
  private var isTafsirPickerVisible = false
  private var tafsirPickerTargetOpen: Boolean? = null
  private var tafsirPickerAnimGen: Int = 0
  private lateinit var tafsirPickerScrim: View
  private lateinit var tafsirPickerSheet: LinearLayout
  private lateinit var tafsirPickerHandle: View
  private lateinit var tafsirPickerTitle: TextView
  private lateinit var tafsirPickerCloseBtn: LinearLayout
  private lateinit var tafsirPickerCloseText: TextView
  private lateinit var tafsirPickerCloseIcon: VectorPathDrawable
  private lateinit var tafsirPickerScroll: ScrollView
  private lateinit var tafsirPickerListCol: LinearLayout
  private lateinit var tafsirPickerDownloadCta: TextView
  private data class TafsirPickerRowHolder(
    val filename: String,
    val label: String,
    val row: LinearLayout,
    val titleView: TextView,
    val checkView: ImageView,
    val checkIcon: VectorPathDrawable,
    val bgDrawable: GradientDrawable
  )
  private val tafsirPickerRows = ArrayList<TafsirPickerRowHolder>()

  // Standalone Detached Z4 Reader Appearance Sheet (completely separate from 2-tab Study Sheet)
  private var isAppearanceSheetBuilt = false
  private var isAppearanceSheetRenderVisible = false
  private var appearanceSheetTargetOpen: Boolean? = null
  private var appearanceSheetAnimGen: Int = 0
  private lateinit var appearanceSheetScrim: View
  private lateinit var appearanceSheetCard: LinearLayout
  private lateinit var appearanceSheetHandle: View
  private lateinit var appearanceSheetTitle: TextView
  private lateinit var appearanceSheetDoneBtn: LinearLayout
  private lateinit var appearanceSheetDoneText: TextView
  private lateinit var appearanceSheetCloseIcon: VectorPathDrawable
  private lateinit var appearanceSheetScroll: ScrollView
  private val appModeButtons = LinkedHashMap<String, TextView>()
  private val appModeBgs = LinkedHashMap<String, GradientDrawable>()
  private val appStyleChips = LinkedHashMap<AppThemeStyle, Pair<LinearLayout, TextView>>()
  private val appStyleBgs = LinkedHashMap<AppThemeStyle, GradientDrawable>()
  private val appStyleSwatches = LinkedHashMap<AppThemeStyle, View>()
  private val appStyleSwatchBgs = LinkedHashMap<AppThemeStyle, DuoToneSwatchDrawable>()
  private val appStyleCheckViews = LinkedHashMap<AppThemeStyle, ImageView>()
  private val appStyleCheckIcons = LinkedHashMap<AppThemeStyle, VectorPathDrawable>()
  private val pageColorButtons = LinkedHashMap<PageColor, TextView>()
  private val pageColorBgs = LinkedHashMap<PageColor, GradientDrawable>()
  private var lastAppearancePalette: HybridViewPalette? = null
  private var lastAppearanceMode: String? = null
  private var lastAppearanceStyle: AppThemeStyle? = null
  private var lastAppearancePageColor: PageColor? = null
  private lateinit var oledToggleRow: LinearLayout
  private lateinit var oledTitleView: TextView
  private lateinit var oledSubtitleView: TextView
  private lateinit var oledSwitchTrack: FrameLayout
  private lateinit var oledSwitchThumb: View
  private lateinit var oledRowBg: GradientDrawable
  private lateinit var oledTrackBg: GradientDrawable
  private lateinit var oledThumbBg: GradientDrawable
  private var isOledToggleChecked: Boolean = false
  private lateinit var matchAppToggleRow: LinearLayout
  private lateinit var matchAppLabel: TextView
  private lateinit var matchAppCheckboxIcon: ImageView
  private lateinit var matchAppBoxBg: GradientDrawable
  private lateinit var matchAppCheckDrawable: VectorPathDrawable
  private var isMatchAppChecked: Boolean = false
  private lateinit var textBrightnessLabel: TextView
  private lateinit var textBrightnessSeek: HybridBrightnessSliderView
  private lateinit var bgBrightnessLabel: TextView
  private lateinit var bgBrightnessSeek: HybridBrightnessSliderView
  private lateinit var nightControlsBox: LinearLayout
  private val appearanceLabels = ArrayList<TextView>()

  private var controller: HybridReaderController? = null
  private var collectionJob: Job? = null
  private var state = HybridReaderUiState()
  private var palette = HybridViewPalette.from(AppThemeStyle.BEIGE, false)
  private var lastResolvedStyle: AppThemeStyle? = null
  private var lastResolvedDarkUi: Boolean? = null
  private var lastResolvedOledUi: Boolean? = null
  private var lastSuraTitleText: String? = null
  private var lastPageNumberText: String? = null
  private var lastAyahTagText: String? = null
  private var lastQariTitleText: String? = null
  private var lastCapsuleMetaText: String? = null
  private var idlePrebuildScheduled = false
  private val idlePrebuildHandler = MessageQueue.IdleHandler {
    if (!isAttachedToWindow) return@IdleHandler false
    prewarmStudyDrawerVectorBitmaps()
    ensureFloatingAyahBarBuilt()
    prewarmFloatingAyahBarMeasure()
    ensureStudyDrawerBuilt()
    ensureRecitationTabBuilt()
    updateActiveDrawerTabView(recitationTabRoot)
    renderRecitationTab(state)
    if (studyDrawerTabHost.width <= 0 || studyDrawerTabHost.height <= 0) {
      val vw = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
      val drawerH = computeStudyDrawerHeightPx(HybridReaderContract.StudyTab.RECITATION, expanded = false)
      val marginH = dp(STUDY_SHEET_HORIZONTAL_INSET_DP)
      val marginB = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
      val sheetW = (vw - marginH * 2).coerceAtLeast(1)
      if (sheetW > 0 && drawerH > 0) {
        studyDrawerSheet.measure(
          MeasureSpec.makeMeasureSpec(sheetW, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(drawerH, MeasureSpec.EXACTLY)
        )
        val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val top = (vh - marginB - drawerH).coerceAtLeast(0)
        studyDrawerSheet.layout(marginH, top, marginH + sheetW, top + drawerH)
      }
    }
    premeasureTabInDrawer(recitationTabRoot)
    false
  }
  private var postOpenPrebuildScheduled = false
  private val prebuildTafsirRunnable = Runnable {
    if (!isAttachedToWindow || !state.isStudyDrawerOpen) return@Runnable
    if (!isTafsirTabBuilt) {
      ensureTafsirTabBuilt()
      renderTafsirTab(state)
      premeasureTabInDrawer(tafsirTabRoot)
    }
  }
  private var statusTopPx = 0
  private var navBottomPx = 0
  private var lastNudgeSentPx = 0f

  private var onBackToHub: (() -> Unit)? = null
  private var onJumpPage: (() -> Unit)? = null
  private var onTogglePageBookmark: (() -> Unit)? = null
  private var onAyahListen: ((SuraAyah) -> Unit)? = null
  private var onAyahTafsir: ((SuraAyah) -> Unit)? = null
  private var onAyahBookmark: ((SuraAyah) -> Unit)? = null
  private var onAyahCopy: ((SuraAyah, SuraAyah) -> Unit)? = null
  private var onAyahShare: ((SuraAyah, SuraAyah) -> Unit)? = null
  private var onPlayPause: (() -> Unit)? = null
  private var onSkipNext: (() -> Unit)? = null
  private var onSkipPrev: (() -> Unit)? = null
  private var onStop: (() -> Unit)? = null
  private var onSpeedChanged: ((Float) -> Unit)? = null
  private var onRepeatChanged: ((Int) -> Unit)? = null
  private var onShowQariList: (() -> Unit)? = null
  private var onCancelDownload: (() -> Unit)? = null
  private var onAllowMobileDownload: (() -> Unit)? = null
  private var onOpenTafsirManager: (() -> Unit)? = null
  private var onTafsirStepAyah: ((Boolean) -> Unit)? = null
  private var onAppThemeStyleSelected: ((AppThemeStyle) -> Unit)? = null
  private var onAppThemeModeSelected: ((String) -> Unit)? = null
  private var onOledDarkModeChanged: ((Boolean) -> Unit)? = null
  private var onPageAppearanceUpdated: (() -> Unit)? = null
  private var onMushafNudgeChanged: ((Float) -> Unit)? = null

  private val progressHandler = Handler(Looper.getMainLooper())
  private var progressTickActive = false
  private var syntheticProgressFraction = 0.2f
  private var lastPlayTickMs = 0L
  private val progressRunnable = object : Runnable {
    override fun run() {
      if (!progressTickActive) return
      val now = android.os.SystemClock.uptimeMillis()
      val dt = (now - lastPlayTickMs).coerceIn(0L, 1000L)
      lastPlayTickMs = now
      val dur = state.ayahDurationMs.takeIf { it > 0L } ?: 6000L
      syntheticProgressFraction = (syntheticProgressFraction + dt.toFloat() / dur.toFloat()) % 1f
      updateProgressFillWidth()
      progressHandler.postDelayed(this, 250L)
    }
  }

  private var isSyncingAppearanceControls = false

  init {
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipChildren = false
    clipToPadding = false

    buildTopPills()
    buildAudioCapsule()

    applyPalette(palette)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!idlePrebuildScheduled) {
      idlePrebuildScheduled = true
      post {
        if (isAttachedToWindow) {
          Looper.myQueue().addIdleHandler(idlePrebuildHandler)
        }
      }
    }
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (h > 0 && h != oldh) {
      if (isStudyDrawerBuilt) {
        val drawerHeight = computeStudyDrawerHeightPx()
        (studyDrawerSheet.layoutParams as? LayoutParams)?.let { lp ->
          if (lp.height != drawerHeight) {
            lp.height = drawerHeight
            studyDrawerSheet.layoutParams = lp
          }
        }
        if (isTafsirTabBuilt && state.isStudyDrawerOpen && state.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR) {
          scheduleTafsirOverflowCheck()
        }
      }
      if (isAppearanceSheetBuilt) {
        updateAppearanceSheetMaxHeight()
      }
      if (isTafsirPickerBuilt) {
        updateTafsirPickerMaxHeight()
      }
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    val maxViewportY = height.takeIf { it > 0 }?.toFloat() ?: Float.POSITIVE_INFINITY
    val validBounds = state.ayahOverlayBounds?.takeIf {
      it.isValidForOverlay(minBottomPx = statusTopPx.toFloat(), maxTopPx = maxViewportY)
    }
    if (isAyahBarBuilt &&
      ayahBarWrapper.visibility == View.VISIBLE &&
      state.selectedStart != null &&
      !state.isAnySheetOpen &&
      validBounds != null &&
      (topPillsRow.bottom != lastMeasuredTopPillsBottom || audioCapsuleOuter.top != lastMeasuredAudioCapsuleTop)
    ) {
      lastMeasuredTopPillsBottom = topPillsRow.bottom
      lastMeasuredAudioCapsuleTop = audioCapsuleOuter.top
      post {
        val freshMaxY = height.takeIf { it > 0 }?.toFloat() ?: Float.POSITIVE_INFINITY
        val freshBounds = state.ayahOverlayBounds?.takeIf {
          it.isValidForOverlay(minBottomPx = statusTopPx.toFloat(), maxTopPx = freshMaxY)
        }
        if (isAttachedToWindow &&
          isAyahBarBuilt &&
          ayahBarWrapper.visibility == View.VISIBLE &&
          state.selectedStart != null &&
          !state.isAnySheetOpen &&
          freshBounds != null
        ) {
          updateFloatingAyahBarPosition(freshBounds)
        }
      }
    }
  }

  private fun dp(v: Float): Int = HybridViewTokens.dp(context, v)
  private fun dpF(v: Float): Float = HybridViewTokens.dpF(context, v)

  private fun buildTopPills() {
    topPillsRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      setPadding(dp(12f), dp(8f), dp(12f), dp(4f))
    }

    // Navigation pill (right/start in RTL)
    backButton.apply {
      setImageDrawable(backIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_back_cd)
      isClickable = true
      isFocusable = true
      setOnClickListener { onBackToHub?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    suraTitleText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    pageNumberText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      maxLines = 1
    }
    suraPageJumpArea.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      val ph = dp(8f)
      val pv = dp(6f)
      setPadding(ph, pv, ph, pv)
      setOnClickListener { onJumpPage?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      addView(suraTitleText)
      addView(
        navPageDivider,
        LinearLayout.LayoutParams(dp(1f), dp(12f)).apply {
          marginStart = dp(6f)
          marginEnd = dp(6f)
        }
      )
      addView(pageNumberText)
    }

    navPill.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      clipChildren = false
      clipToPadding = false
      minimumHeight = dp(38f)
      setPadding(dp(4f), dp(2f), dp(6f), dp(2f))
      addView(backButton, LinearLayout.LayoutParams(dp(34f), dp(34f)))
      addView(
        navDivider,
        LinearLayout.LayoutParams(dp(1f), dp(14f)).apply {
          marginStart = dp(2f)
          marginEnd = dp(2f)
        }
      )
      addView(suraPageJumpArea)
    }

    // Tools pill (left/end in RTL: Save | Appearance — Tafsir lives in Study Drawer & Ayah Action Bar)
    fun setupToolButton(
      btn: LinearLayout,
      iconView: ImageView,
      icon: VectorPathDrawable,
      tv: TextView,
      labelRes: Int,
      onClick: () -> Unit
    ) {
      tv.apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
      }
      iconView.setImageDrawable(icon)
      btn.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumWidth = dp(44f)
        minimumHeight = dp(34f)
        val ph = dp(10f)
        val pv = dp(5f)
        setPadding(ph, pv, ph, pv)
        addView(iconView)
        addView(
          tv,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { marginStart = dp(4f) }
        )
        setOnClickListener { onClick() }
        HybridViewTokens.attachApplePressFeedback(this, 0.965f)
      }
    }

    setupToolButton(
      toolSaveBtn,
      toolSaveIconView,
      toolSaveOutlineIcon,
      toolSaveText,
      R.string.hybrid_reader_tool_save
    ) {
      onTogglePageBookmark?.invoke()
    }
    setupToolButton(
      toolAppearanceBtn,
      ImageView(context),
      toolAppearanceIcon,
      toolAppearanceText,
      R.string.hybrid_reader_tool_appearance
    ) {
      controller?.openAppearanceSheet()
    }

    toolsPill.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      clipChildren = false
      clipToPadding = false
      minimumHeight = dp(38f)
      setPadding(dp(4f), dp(2f), dp(4f), dp(2f))
      addView(toolSaveBtn)
      addView(toolDivider1, LinearLayout.LayoutParams(dp(1f), dp(14f)))
      addView(toolAppearanceBtn)
    }

    topPillsRow.addView(navPill)
    topPillsRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
    topPillsRow.addView(toolsPill)

    addView(
      topPillsRow,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP)
    )
  }

  private fun ensureFloatingAyahBarBuilt() {
    if (isAyahBarBuilt) return
    isAyahBarBuilt = true
    ayahBarWrapper = LinearLayout(context)
    ayahSelectionTagPill = TextView(context)
    ayahActionPill = LinearLayout(context)
    ayahCaretTop = CaretView(context, pointUp = true)
    ayahCaretBottom = CaretView(context, pointUp = false)

    ayahBarWrapper.apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      visibility = View.INVISIBLE
    }

    ayahSelectionTagPill.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      val ph = dp(12f)
      val pv = dp(4f)
      setPadding(ph, pv, ph, pv)
    }

    ayahActionPill.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val ph = dp(8f)
      val pv = dp(6f)
      setPadding(ph, pv, ph, pv)
    }

    val actions = listOf(
      Triple(HybridArchitecturalIcons.PLAY, R.string.hybrid_reader_action_listen) {
        state.selectedStart?.let { onAyahListen?.invoke(it) }
      },
      Triple(HybridArchitecturalIcons.TAFSIR, R.string.hybrid_reader_action_tafsir) {
        state.selectedStart?.let { onAyahTafsir?.invoke(it) }
      },
      Triple(HybridArchitecturalIcons.BOOKMARK, R.string.hybrid_reader_action_bookmark) {
        state.selectedStart?.let { onAyahBookmark?.invoke(it) }
      },
      Triple(HybridArchitecturalIcons.COPY, R.string.hybrid_reader_action_copy) {
        val s = state.selectedStart
        val e = state.selectedEnd ?: s
        if (s != null && e != null) onAyahCopy?.invoke(s, e)
      },
      Triple(HybridArchitecturalIcons.SHARE, R.string.hybrid_reader_action_share) {
        val s = state.selectedStart
        val e = state.selectedEnd ?: s
        if (s != null && e != null) onAyahShare?.invoke(s, e)
      }
    )

    val actionIconSize = dp(15f)
    actions.forEachIndexed { idx, (spec, labelRes, handler) ->
      val initialTint = when (idx) {
        0 -> palette.accent
        2 -> palette.editorialAccent
        else -> palette.textPrimary
      }
      val iconDrawable = HybridArchitecturalIcons.drawable(spec, initialTint, actionIconSize)
      val iconImageView = ImageView(context).apply { setImageDrawable(iconDrawable) }
      val labelView = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        gravity = Gravity.CENTER
      }
      ayahActionIcons.add(iconDrawable)
      ayahActionTexts.add(labelView)
      if (idx == 2) {
        ayahBookmarkImageView = iconImageView
        ayahBookmarkOutlineIcon = iconDrawable
        ayahBookmarkFilledIcon = HybridArchitecturalIcons.drawable(
          HybridArchitecturalIcons.BOOKMARK_FILLED,
          palette.editorialAccent,
          actionIconSize
        )
        ayahBookmarkLabelView = labelView
      }

      val btnBg = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = dpF(12f),
        pressColor = palette.ripple
      )
      ayahActionButtonBgs.add(btnBg)
      val btn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        val ph = dp(9f)
        val pv = dp(4f)
        setPadding(ph, pv, ph, pv)
        background = btnBg
        addView(iconImageView)
        addView(
          labelView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = dp(2f) }
        )
        setOnClickListener { handler() }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      ayahActionButtons.add(btn)
      ayahActionPill.addView(
        btn,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
    }

    val caretW = dp(14f)
    val caretH = dp(7f)
    ayahBarWrapper.addView(ayahCaretTop, LinearLayout.LayoutParams(caretW, caretH))
    ayahBarWrapper.addView(
      ayahSelectionTagPill,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { bottomMargin = dp(6f) }
    )
    ayahBarWrapper.addView(
      ayahActionPill,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    )
    ayahBarWrapper.addView(ayahCaretBottom, LinearLayout.LayoutParams(caretW, caretH))

    val insertIdx = listOfNotNull(
      if (isStudyDrawerBuilt) indexOfChild(studyDrawerScrim).takeIf { it >= 0 } else null,
      if (isAppearanceSheetBuilt) indexOfChild(appearanceSheetScrim).takeIf { it >= 0 } else null,
      if (isTafsirPickerBuilt) indexOfChild(tafsirPickerScrim).takeIf { it >= 0 } else null
    ).minOrNull() ?: childCount
    addView(
      ayahBarWrapper,
      insertIdx,
      LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT)
    )
    applyAyahBarPalette(palette)
  }

  private fun buildAudioCapsule() {
    audioCapsuleOuter.apply {
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      setPadding(dp(12f), dp(8f), dp(12f), dp(10f))
    }

    capsulePlayBtn.apply {
      setImageDrawable(capsulePlayIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_play_cd)
      isClickable = true
      isFocusable = true
      setOnClickListener { onPlayPause?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }

    capsuleQariTitle.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    capsuleMetaText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }

    capsuleDownloadText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    capsuleDownloadAction.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      isClickable = true
      isFocusable = true
      setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
    }
    capsuleDownloadRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      visibility = View.GONE
      addView(capsuleDownloadText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(capsuleDownloadAction)
    }

    capsuleExpandBtn.apply {
      setImageDrawable(capsuleExpandIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_expand_drawer_cd)
      isClickable = true
      isFocusable = true
      setOnClickListener { controller?.openStudyDrawer() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }

    val topRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      clipChildren = false
      clipToPadding = false
      val playSize = dp(38f)
      addView(capsulePlayBtn, LinearLayout.LayoutParams(playSize, playSize))
      val midCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(capsuleQariTitle)
        addView(capsuleMetaText)
        addView(capsuleDownloadRow)
      }
      addView(
        midCol,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginStart = dp(10f)
          marginEnd = dp(8f)
        }
      )
      val expandSize = dp(32f)
      addView(capsuleExpandBtn, LinearLayout.LayoutParams(expandSize, expandSize))
    }

    val progressBox = FrameLayout(context).apply {
      addView(
        capsuleProgressTrack,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(2.5f))
      )
      addView(
        capsuleProgressFill,
        FrameLayout.LayoutParams(0, dp(2.5f), Gravity.START)
      )
    }

    audioCapsuleCard.apply {
      orientation = LinearLayout.VERTICAL
      clipChildren = false
      clipToPadding = false
      isClickable = true
      setPadding(dp(12f), dp(8f), dp(12f), dp(6f))
      setOnClickListener { controller?.openStudyDrawer() }
      HybridViewTokens.attachApplePressFeedback(this, 0.985f)
      addView(topRow)
      addView(
        progressBox,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          dp(2.5f)
        ).apply { topMargin = dp(6f) }
      )
    }

    audioCapsuleOuter.addView(
      audioCapsuleCard,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
      )
    )
    addView(
      audioCapsuleOuter,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    )
  }

  private fun ensureStudyDrawerBuilt() {
    if (isStudyDrawerBuilt) return
    isStudyDrawerBuilt = true
    studyDrawerScrim = object : View(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isStudyDrawerRenderVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    studyDrawerSheet = object : LinearLayout(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isStudyDrawerRenderVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    drawerHandle = View(context)
    drawerCollapseIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHEVRON_DOWN, Color.BLACK, dp(18f))
    drawerCollapseBtn = ImageView(context)
    drawerTabRail = LinearLayout(context)
    studyDrawerTabHost = object : FrameLayout(context) {
      override fun hasOverlappingRendering(): Boolean = false

      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isStudyDrawerRenderVisible) return false
        val target = activeDrawerTabView ?: return false
        if (!target.isAttachedToWindow || target.visibility != View.VISIBLE) return false
        val offsetX = scrollX - target.left.toFloat()
        val offsetY = scrollY - target.top.toFloat()
        if (offsetX == 0f && offsetY == 0f && target.matrix.isIdentity) {
          return target.dispatchTouchEvent(ev)
        }
        val copy = MotionEvent.obtain(ev)
        copy.offsetLocation(offsetX, offsetY)
        if (!target.matrix.isIdentity) {
          val inv = Matrix()
          target.matrix.invert(inv)
          copy.transform(inv)
        }
        val handled = target.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
      }
    }

    studyDrawerScrim.apply {
      visibility = View.INVISIBLE
      isClickable = false
      isFocusable = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      setOnClickListener { controller?.closeStudyDrawer() }
    }
    val studyInsertIdx = listOfNotNull(
      if (isAppearanceSheetBuilt) indexOfChild(appearanceSheetScrim).takeIf { it >= 0 } else null,
      if (isTafsirPickerBuilt) indexOfChild(tafsirPickerScrim).takeIf { it >= 0 } else null
    ).minOrNull() ?: childCount
    addView(
      studyDrawerScrim,
      studyInsertIdx,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )

    studyDrawerSheet.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
      isClickable = true
      setPadding(dp(16f), dp(12f), dp(16f), dp(14f))
    }

    // Handle + collapse row (tapping handle area on Tafsir tab toggles expand/collapse when overflowing)
    drawerCollapseBtn.apply {
      setImageDrawable(drawerCollapseIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_collapse_drawer_cd)
      isClickable = true
      isFocusable = true
      setOnClickListener { controller?.closeStudyDrawer() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    val topBar = FrameLayout(context).apply {
      isClickable = true
      setOnClickListener {
        if (state.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR &&
          (tafsirHasOverflow || isTafsirExpanded)
        ) {
          toggleTafsirExpanded()
        }
      }
      addView(
        drawerHandle,
        FrameLayout.LayoutParams(dp(38f), dp(4f), Gravity.CENTER)
      )
      addView(
        drawerCollapseBtn,
        FrameLayout.LayoutParams(dp(32f), dp(32f), Gravity.END or Gravity.CENTER_VERTICAL)
      )
    }
    studyDrawerSheet.addView(topBar)

    // Recessed 2-tab rail: التلاوة | التفسير (Appearance lives in dedicated standalone sheet!)
    drawerTabRail.apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
    }
    val tabs = listOf(
      HybridReaderContract.StudyTab.RECITATION to R.string.hybrid_reader_tab_recitation,
      HybridReaderContract.StudyTab.TAFSIR to R.string.hybrid_reader_tab_tafsir
    )
    val railRadius = dpF(999f)
    val tabOutlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(target: View, outline: Outline) {
        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) return
        val clamped = minOf(railRadius, minOf(w, h) * 0.5f)
        outline.setRoundRect(0, 0, w, h, clamped)
        outline.alpha = 1f
      }
    }
    tabs.forEach { (tab, labelRes) ->
      val pressDrawable = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = railRadius,
        pressColor = palette.ripple
      )
      drawerTabPressDrawables[tab] = pressDrawable
      val tv = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
        isFocusable = true
        minimumHeight = dp(34f)
        outlineProvider = tabOutlineProvider
        setOnClickListener { controller?.selectStudyTab(tab) }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      drawerTabButtons[tab] = tv
      drawerTabRail.addView(
        tv,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      )
    }
    studyDrawerSheet.addView(
      drawerTabRail,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = dp(8f)
        bottomMargin = dp(10f)
      }
    )

    studyDrawerTabHost.clipChildren = false
    studyDrawerTabHost.clipToPadding = false
    studyDrawerSheet.addView(
      studyDrawerTabHost,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    )

    val marginH = dp(STUDY_SHEET_HORIZONTAL_INSET_DP)
    addView(
      studyDrawerSheet,
      studyInsertIdx + 1,
      LayoutParams(LayoutParams.MATCH_PARENT, computeStudyDrawerHeightPx(), Gravity.BOTTOM).apply {
        leftMargin = marginH
        rightMargin = marginH
        bottomMargin = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
      }
    )
    applyStudyDrawerPalette(palette)
  }

  private fun ensureRecitationTabBuilt() {
    if (isRecitationTabBuilt) return
    isRecitationTabBuilt = true
    recitationTabRoot = object : ScrollView(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }.apply {
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
      overScrollMode = View.OVER_SCROLL_NEVER
      clipChildren = false
      clipToPadding = false
    }
    recQariNameText = TextView(context)
    recQariSubtitleText = TextView(context)
    recQariSwitchBtn = TextView(context)
    recDownloadRow = LinearLayout(context)
    recDownloadText = TextView(context)
    recDownloadAction = TextView(context)
    recPrevIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.NEXT, Color.BLACK, dp(18f))
    recPrevBtn = ImageView(context)
    recPlayIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PLAY, Color.WHITE, dp(20f))
    recPauseIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PAUSE, Color.WHITE, dp(20f))
    recPlayBtn = ImageView(context)
    lastRenderedRecPlaying = false
    recNextIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PREV, Color.BLACK, dp(18f))
    recNextBtn = ImageView(context)
    recStopIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.STOP, Color.BLACK, dp(15f))
    recStopBtn = ImageView(context)
    recSpeedBtn = TextView(context)
    recRepeatBtn = TextView(context)
    val body = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
    }

    recQariNameText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    recQariSubtitleText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    recQariSwitchBtn.apply {
      text = context.getString(R.string.hybrid_reader_qari_switch)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      isClickable = true
      isFocusable = true
      setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
      setOnClickListener { onShowQariList?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    val qariRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      val info = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(recQariNameText)
        addView(recQariSubtitleText)
      }
      addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(recQariSwitchBtn)
    }
    body.addView(qariRow)

    recDownloadText.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    recDownloadAction.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      isClickable = true
      isFocusable = true
      setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    recDownloadRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      visibility = View.GONE
      setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
      addView(recDownloadText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(recDownloadAction)
    }
    body.addView(
      recDownloadRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(8f) }
    )

    recPrevBtn.apply {
      setImageDrawable(recPrevIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_previous_ayah_cd)
      isClickable = true
      setOnClickListener { onSkipPrev?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    recPlayBtn.apply {
      setImageDrawable(recPlayIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_play_cd)
      isClickable = true
      setOnClickListener { onPlayPause?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    recNextBtn.apply {
      setImageDrawable(recNextIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_next_ayah_cd)
      isClickable = true
      setOnClickListener { onSkipNext?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    recStopBtn.apply {
      setImageDrawable(recStopIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.stop)
      visibility = View.GONE
      isClickable = true
      isFocusable = true
      setOnClickListener { onStop?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    recSpeedBtn.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      textDirection = View.TEXT_DIRECTION_LTR
      gravity = Gravity.CENTER
      minWidth = dp(52f)
      isClickable = true
      setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
      setOnClickListener {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val idx = speeds.indexOfFirst { abs(it - state.playbackSpeed) < 0.05f }
        val next = speeds[(idx + 1) % speeds.size]
        onSpeedChanged?.invoke(next)
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    recRepeatBtn.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      gravity = Gravity.CENTER
      minWidth = dp(52f)
      isClickable = true
      setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
      setOnClickListener {
        val repeats = listOf(0, 1, 3, 5, -1)
        val idx = repeats.indexOf(state.repeatCount)
        val next = repeats[(idx + 1) % repeats.size]
        onRepeatChanged?.invoke(next)
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }

    val controlsRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      clipChildren = false
      clipToPadding = false
      setPadding(0, dp(14f), 0, dp(4f))
      addView(recSpeedBtn)
      addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
      addView(recPrevBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
      addView(
        recPlayBtn,
        LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
          marginStart = dp(8f)
          marginEnd = dp(8f)
        }
      )
      addView(recNextBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))
      addView(
        recStopBtn,
        LinearLayout.LayoutParams(dp(34f), dp(34f)).apply {
          marginStart = dp(2f)
        }
      )
      addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
      addView(recRepeatBtn)
    }
    body.addView(controlsRow)
    recitationTabRoot.addView(body)
    studyDrawerTabHost.addView(
      recitationTabRoot,
      FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    )
    applyRecitationTabPalette(palette)
  }

  private fun ensureTafsirTabBuilt() {
    if (isTafsirTabBuilt) return
    isTafsirTabBuilt = true
    tafsirTabRoot = object : LinearLayout(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }
    tafsirAyahTitle = TextView(context)
    tafsirListIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.INDEX, palette.accent, dp(13f))
    tafsirListText = TextView(context)
    tafsirListBtn = LinearLayout(context)
    tafsirPrevIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.NEXT, palette.accent, dp(16f))
    tafsirNextIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.PREV, palette.accent, dp(16f))
    tafsirPrevBtn = ImageView(context)
    tafsirNextBtn = ImageView(context)
    tafsirSourceScroll = HorizontalScrollView(context)
    tafsirSourceRow = LinearLayout(context)
    tafsirBodyCard = FrameLayout(context)
    tafsirBodyScroll = ScrollView(context)
    tafsirBodyText = TextView(context)
    tafsirOverflowFade = View(context)
    tafsirExpandIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHEVRON_UP, palette.accent, dp(15f))
    tafsirExpandIconView = ImageView(context)
    tafsirExpandBtn = FrameLayout(context)
    tafsirEmptyCard = LinearLayout(context)
    tafsirEmptyTitle = TextView(context)
    tafsirEmptyBody = TextView(context)
    tafsirEmptyCta = Button(context)
    tafsirTabRoot.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    tafsirAyahTitle.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    tafsirListText.apply {
      text = context.getString(R.string.hybrid_reader_tafsir_list)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }
    tafsirListBtn.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      contentDescription = context.getString(R.string.hybrid_reader_tafsir_list)
      setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
      addView(ImageView(context).apply { setImageDrawable(tafsirListIcon) })
      addView(
        tafsirListText,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(5f) }
      )
      setOnClickListener {
        if (state.tafsirState.availableSources.isEmpty()) {
          onOpenTafsirManager?.invoke()
        } else {
          openTafsirPicker()
        }
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    tafsirPrevBtn.apply {
      setImageDrawable(tafsirPrevIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_previous_ayah_cd)
      isClickable = true
      setOnClickListener { onTafsirStepAyah?.invoke(false) }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    tafsirNextBtn.apply {
      setImageDrawable(tafsirNextIcon)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_reader_next_ayah_cd)
      isClickable = true
      setOnClickListener { onTafsirStepAyah?.invoke(true) }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }

    val navRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(
        tafsirAyahTitle,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginEnd = dp(6f)
        }
      )
      addView(
        tafsirListBtn,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(4f) }
      )
      addView(tafsirPrevBtn, LinearLayout.LayoutParams(dp(32f), dp(32f)))
      addView(tafsirNextBtn, LinearLayout.LayoutParams(dp(32f), dp(32f)))
    }
    tafsirTabRoot.addView(navRow)

    tafsirSourceRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    tafsirSourceScroll.apply {
      isHorizontalScrollBarEnabled = false
      addView(tafsirSourceRow)
    }
    tafsirTabRoot.addView(
      tafsirSourceScroll,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = dp(6f)
        bottomMargin = dp(8f)
      }
    )

    tafsirBodyText.apply {
      id = R.id.tafsir_body
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setLineSpacing(0f, 1.45f)
    }
    tafsirBodyScroll.apply {
      overScrollMode = View.OVER_SCROLL_NEVER
      clipToPadding = false
      setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
      addView(
        tafsirBodyText,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
      )
    }
    tafsirOverflowFade.apply {
      visibility = View.GONE
      isClickable = false
      isFocusable = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    tafsirExpandIconView.apply {
      setImageDrawable(tafsirExpandIcon)
      scaleType = ImageView.ScaleType.CENTER
    }
    tafsirExpandBtn.apply {
      visibility = View.GONE
      isClickable = true
      isFocusable = true
      contentDescription = context.getString(R.string.hybrid_reader_tafsir_expand_cd)
      addView(
        tafsirExpandIconView,
        FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
      )
      setOnClickListener { toggleTafsirExpanded() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }
    tafsirBodyCard.apply {
      clipChildren = true
      clipToPadding = false
      addView(
        tafsirBodyScroll,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
      )
      addView(
        tafsirOverflowFade,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(34f), Gravity.BOTTOM)
      )
      addView(
        tafsirExpandBtn,
        FrameLayout.LayoutParams(dp(44f), dp(24f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
          bottomMargin = dp(6f)
        }
      )
    }
    tafsirTabRoot.addView(
      tafsirBodyCard,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    )

    tafsirEmptyTitle.apply {
      text = context.getString(R.string.hybrid_reader_tafsir_empty_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    tafsirEmptyBody.apply {
      text = context.getString(R.string.hybrid_reader_tafsir_empty_body)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setLineSpacing(0f, 1.3f)
    }
    tafsirEmptyCta.apply {
      id = R.id.tafsir_empty_cta
      text = context.getString(R.string.hybrid_reader_tafsir_download)
      isAllCaps = false
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      val ph = dp(18f)
      val pv = dp(8f)
      setPadding(ph, pv, ph, pv)
      setOnClickListener { onOpenTafsirManager?.invoke() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    tafsirEmptyCard.apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
      visibility = View.GONE
      addView(tafsirEmptyTitle)
      addView(
        tafsirEmptyBody,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6f) }
      )
      addView(
        tafsirEmptyCta,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12f) }
      )
    }
    tafsirTabRoot.addView(tafsirEmptyCard)
    studyDrawerTabHost.addView(
      tafsirTabRoot,
      FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    )
    applyTafsirTabPalette(palette)
  }

  private fun ensureTafsirPickerBuilt() {
    if (isTafsirPickerBuilt) return
    isTafsirPickerBuilt = true
    tafsirPickerScrim = object : View(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isTafsirPickerVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    tafsirPickerSheet = object : LinearLayout(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isTafsirPickerVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    tafsirPickerHandle = View(context)
    tafsirPickerTitle = TextView(context)
    tafsirPickerCloseBtn = LinearLayout(context)
    tafsirPickerCloseText = TextView(context)
    tafsirPickerCloseIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CLOSE, palette.textSecondary, dp(14f))
    tafsirPickerScroll = ScrollView(context)
    tafsirPickerListCol = LinearLayout(context)
    tafsirPickerDownloadCta = TextView(context)

    tafsirPickerScrim.apply {
      visibility = View.INVISIBLE
      isClickable = false
      isFocusable = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      setOnClickListener { dismissTafsirPicker() }
    }
    addView(
      tafsirPickerScrim,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )

    tafsirPickerSheet.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
      isClickable = true
      setPadding(dp(18f), dp(12f), dp(18f), dp(16f))
    }

    tafsirPickerSheet.addView(
      tafsirPickerHandle,
      LinearLayout.LayoutParams(dp(38f), dp(4f)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(10f)
      }
    )

    tafsirPickerTitle.apply {
      text = context.getString(R.string.hybrid_reader_tafsir_picker_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    tafsirPickerCloseText.apply {
      text = context.getString(R.string.hybrid_reader_done)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    tafsirPickerCloseBtn.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      contentDescription = context.getString(R.string.hybrid_reader_done)
      setPadding(dp(11f), dp(5f), dp(11f), dp(5f))
      addView(tafsirPickerCloseText)
      addView(
        ImageView(context).apply { setImageDrawable(tafsirPickerCloseIcon) },
        LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginStart = dp(5f) }
      )
      setOnClickListener { dismissTafsirPicker() }
      HybridViewTokens.attachApplePressFeedback(this, 0.965f)
    }
    val pickerHeader = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(tafsirPickerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(tafsirPickerCloseBtn)
    }
    tafsirPickerSheet.addView(pickerHeader)

    tafsirPickerListCol.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      setPadding(0, dp(10f), 0, dp(6f))
    }
    tafsirPickerScroll.apply {
      overScrollMode = View.OVER_SCROLL_NEVER
      addView(tafsirPickerListCol)
    }
    tafsirPickerSheet.addView(
      tafsirPickerScroll,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    )

    tafsirPickerDownloadCta.apply {
      text = context.getString(R.string.hybrid_reader_tafsir_download_more)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      gravity = Gravity.CENTER
      isClickable = true
      isFocusable = true
      minimumHeight = dp(42f)
      setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
      setOnClickListener {
        dismissTafsirPicker()
        onOpenTafsirManager?.invoke()
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    tafsirPickerSheet.addView(
      tafsirPickerDownloadCta,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(8f)
      }
    )

    val marginH = dp(STUDY_SHEET_HORIZONTAL_INSET_DP)
    addView(
      tafsirPickerSheet,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        leftMargin = marginH
        rightMargin = marginH
        bottomMargin = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
      }
    )
    updateTafsirPickerMaxHeight()
    applyTafsirPickerPalette(palette)
  }

  private fun ensureAppearanceSheetBuilt() {
    if (isAppearanceSheetBuilt) return
    isAppearanceSheetBuilt = true
    appearanceSheetScrim = object : View(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isAppearanceSheetRenderVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    appearanceSheetCard = object : LinearLayout(context) {
      override fun hasOverlappingRendering(): Boolean = false
      override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return if (isAppearanceSheetRenderVisible) super.dispatchTouchEvent(ev) else false
      }
    }
    appearanceSheetHandle = View(context)
    appearanceSheetTitle = TextView(context)
    appearanceSheetDoneBtn = LinearLayout(context)
    appearanceSheetDoneText = TextView(context)
    appearanceSheetCloseIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CLOSE, palette.textSecondary, dp(14f))
    appearanceSheetScroll = object : ScrollView(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }.apply {
      overScrollMode = View.OVER_SCROLL_NEVER
      clipChildren = false
      clipToPadding = false
    }
    oledRowBg = HybridViewTokens.roundedDrawable(
      fillColor = palette.surfaceVariant,
      strokeColor = palette.cardHairlineBorder,
      strokeWidthPx = dp(1f),
      radiusPx = dpF(14f)
    )
    oledTrackBg = HybridViewTokens.roundedDrawable(
      fillColor = palette.divider,
      radiusPx = dpF(999f)
    )
    oledThumbBg = HybridViewTokens.roundedDrawable(
      fillColor = Color.WHITE,
      radiusPx = dpF(999f)
    )
    oledTitleView = TextView(context).apply {
      text = context.getString(R.string.hybrid_appearance_oled_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    oledSubtitleView = TextView(context).apply {
      text = context.getString(R.string.hybrid_appearance_oled_subtitle)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      maxLines = 2
      ellipsize = TextUtils.TruncateAt.END
    }
    oledSwitchThumb = View(context).apply {
      background = oledThumbBg
    }
    oledSwitchTrack = FrameLayout(context).apply {
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      background = oledTrackBg
      val trackPad = dp(3f)
      setPadding(trackPad, trackPad, trackPad, trackPad)
      addView(
        oledSwitchThumb,
        FrameLayout.LayoutParams(dp(20f), dp(20f), Gravity.CENTER_VERTICAL or Gravity.START)
      )
    }
    oledToggleRow = object : LinearLayout(context) {
      override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = isOledToggleChecked
        info.text = oledTitleView.text
      }
    }
    matchAppCheckDrawable = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHECK, palette.onAccent, dp(13f))
    matchAppBoxBg = HybridViewTokens.roundedDrawable(
      fillColor = Color.TRANSPARENT,
      strokeColor = palette.textSecondary,
      strokeWidthPx = dp(1.5f),
      radiusPx = dpF(5f)
    )
    matchAppCheckboxIcon = ImageView(context).apply {
      scaleType = ImageView.ScaleType.CENTER
      background = matchAppBoxBg
      setImageDrawable(null)
    }
    matchAppLabel = TextView(context).apply {
      text = context.getString(R.string.hybrid_reader_mushaf_match_app)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
    }
    matchAppToggleRow = object : LinearLayout(context) {
      override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = isMatchAppChecked
        info.text = matchAppLabel.text
      }
    }
    textBrightnessLabel = TextView(context)
    textBrightnessSeek = HybridBrightnessSliderView(context)
    bgBrightnessLabel = TextView(context)
    bgBrightnessSeek = HybridBrightnessSliderView(context)
    nightControlsBox = LinearLayout(context)

    appearanceSheetScrim.apply {
      visibility = View.INVISIBLE
      isClickable = false
      isFocusable = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      setOnClickListener { controller?.closeAppearanceSheet() }
    }
    addView(
      appearanceSheetScrim,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )

    appearanceSheetCard.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      visibility = View.INVISIBLE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
      isClickable = true
      setPadding(dp(18f), dp(12f), dp(18f), dp(16f))
    }

    appearanceSheetCard.addView(
      appearanceSheetHandle,
      LinearLayout.LayoutParams(dp(38f), dp(4f)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(10f)
      }
    )

    // Header: المظهر + تم (Done) pill with monochrome 1.65dp CLOSE icon
    appearanceSheetTitle.apply {
      text = context.getString(R.string.hybrid_reader_appearance_sheet_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    appearanceSheetDoneText.apply {
      text = context.getString(R.string.hybrid_reader_done)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    appearanceSheetDoneBtn.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      contentDescription = context.getString(R.string.hybrid_reader_done)
      setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
      addView(appearanceSheetDoneText)
      addView(
        ImageView(context).apply { setImageDrawable(appearanceSheetCloseIcon) },
        LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginStart = dp(5f) }
      )
      setOnClickListener { controller?.closeAppearanceSheet() }
      HybridViewTokens.attachApplePressFeedback(this, 0.965f)
    }
    val headerRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(appearanceSheetTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(appearanceSheetDoneBtn)
    }
    appearanceSheetCard.addView(headerRow)

    val col = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      setPadding(0, dp(12f), 0, dp(4f))
    }

    fun sectionLabel(resId: Int): TextView {
      return TextView(context).apply {
        text = context.getString(resId)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        appearanceLabels.add(this)
      }
    }

    // 1. App Mode (Auto | Light | Dark) — stays open on tap!
    col.addView(sectionLabel(R.string.hybrid_reader_appearance_app_mode))
    val modeRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
    val modes = listOf(
      Constants.THEME_DEFAULT to R.string.hybrid_reader_mode_auto,
      Constants.THEME_LIGHT to R.string.hybrid_reader_mode_light,
      Constants.THEME_DARK to R.string.hybrid_reader_mode_dark
    )
    val pillRadius = dpF(999f)
    modes.forEachIndexed { idx, (mode, labelRes) ->
      val bgDrawable = HybridViewTokens.roundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = pillRadius
      )
      appModeBgs[mode] = bgDrawable
      val tv = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = dp(35f)
        background = bgDrawable
        setOnClickListener {
          controller?.selectAppThemeMode(mode)
          onAppThemeModeSelected?.invoke(mode)
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      appModeButtons[mode] = tv
      modeRow.addView(
        tv,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (idx < modes.lastIndex) marginEnd = dp(6f)
        }
      )
    }
    col.addView(
      modeRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(6f) }
    )

    // 1b. Pure Black OLED Dark Mode Toggle (visible ONLY in Dark Mode; keeps sheet open!)
    oledToggleRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      visibility = View.GONE
      isClickable = true
      isFocusable = true
      background = oledRowBg
      setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
      val labelsCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(oledTitleView)
        addView(
          oledSubtitleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = dp(2f) }
        )
      }
      addView(
        labelsCol,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginEnd = dp(10f)
        }
      )
      addView(
        oledSwitchTrack,
        LinearLayout.LayoutParams(dp(44f), dp(26f))
      )
      setOnClickListener {
        if (!isSyncingAppearanceControls) {
          val nextEnabled = !isOledToggleChecked
          updateOledToggleVisual(nextEnabled)
          controller?.setOledDarkMode(nextEnabled)
          onOledDarkModeChanged?.invoke(nextEnabled)
        }
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    col.addView(
      oledToggleRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(10f) }
    )

    // 2. Duo-Tone Calm Palette (with crisp overlapping 2-dot swatches + check indicator!)
    col.addView(
      sectionLabel(R.string.hybrid_reader_appearance_palette),
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(14f) }
    )
    val styleRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
    }
    val styles = listOf(
      AppThemeStyle.DEFAULT to R.string.hybrid_reader_palette_default,
      AppThemeStyle.GREEN to R.string.hybrid_reader_palette_green,
      AppThemeStyle.BLUE to R.string.hybrid_reader_palette_blue,
      AppThemeStyle.BEIGE to R.string.hybrid_reader_palette_beige,
      AppThemeStyle.GRAY to R.string.hybrid_reader_palette_gray
    )
    val swatchW = dp(26f)
    val swatchH = dp(16f)
    val checkPx = dp(11f)
    val chipRadius = dpF(14f)
    styles.forEachIndexed { idx, (style, labelRes) ->
      val bgDrawable = HybridViewTokens.roundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = chipRadius
      )
      appStyleBgs[style] = bgDrawable
      val chip = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(dp(4f), dp(7f), dp(4f), dp(6f))
        background = bgDrawable
        setOnClickListener {
          controller?.selectAppThemeStyle(style)
          onAppThemeStyleSelected?.invoke(style)
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      val swatchBg = DuoToneSwatchDrawable(dpF(1.6f))
      appStyleSwatchBgs[style] = swatchBg
      val swatch = View(context).apply { background = swatchBg }
      appStyleSwatches[style] = swatch
      val label = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER
        maxLines = 1
      }
      val checkDrawable = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHECK, palette.accent, checkPx)
      appStyleCheckIcons[style] = checkDrawable
      val checkView = ImageView(context).apply {
        setImageDrawable(checkDrawable)
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.INVISIBLE
      }
      appStyleCheckViews[style] = checkView
      chip.addView(swatch, LinearLayout.LayoutParams(swatchW, swatchH))
      chip.addView(
        label,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4f) }
      )
      chip.addView(
        checkView,
        LinearLayout.LayoutParams(checkPx, checkPx).apply { topMargin = dp(2f) }
      )
      appStyleChips[style] = chip to label
      styleRow.addView(
        chip,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (idx < styles.lastIndex) marginEnd = dp(5f)
        }
      )
    }
    col.addView(
      styleRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(6f) }
    )

    // 3. Mushaf Page color (Light | Sepia | Dark) — stays open on tap!
    col.addView(
      sectionLabel(R.string.hybrid_reader_appearance_mushaf),
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(14f) }
    )
    val pageRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
    val pageColors = listOf(
      PageColor.Light to R.string.hybrid_reader_mushaf_light,
      PageColor.Sepia to R.string.hybrid_reader_mushaf_sepia,
      PageColor.Dark to R.string.hybrid_reader_mushaf_dark
    )
    pageColors.forEachIndexed { idx, (pageColor, labelRes) ->
      val bgDrawable = HybridViewTokens.roundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = pillRadius
      )
      pageColorBgs[pageColor] = bgDrawable
      val tv = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = dp(35f)
        background = bgDrawable
        setOnClickListener {
          controller?.selectPageColor(pageColor)
          onPageAppearanceUpdated?.invoke()
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      pageColorButtons[pageColor] = tv
      pageRow.addView(
        tv,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (idx < pageColors.lastIndex) marginEnd = dp(6f)
        }
      )
    }
    col.addView(
      pageRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(6f) }
    )

    // 4. Match App Theme checkbox row — stays open on tap!
    matchAppToggleRow.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      isClickable = true
      isFocusable = true
      minimumHeight = dp(38f)
      setPadding(dp(2f), dp(6f), dp(2f), dp(6f))
      addView(matchAppCheckboxIcon, LinearLayout.LayoutParams(dp(20f), dp(20f)))
      addView(
        matchAppLabel,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginStart = dp(10f)
        }
      )
      setOnClickListener {
        if (!isSyncingAppearanceControls) {
          val nextChecked = !isMatchAppChecked
          updateMatchAppToggleVisual(nextChecked)
          controller?.setMatchAppTheme(nextChecked)
          onPageAppearanceUpdated?.invoke()
        }
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    col.addView(
      matchAppToggleRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dp(8f) }
    )

    // 5. Night Mode Brightness sliders — stays open while adjusting!
    nightControlsBox.apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      textBrightnessLabel.apply {
        text = context.getString(R.string.hybrid_reader_night_text_brightness)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        appearanceLabels.add(this)
      }
      textBrightnessSeek.apply {
        contentDescription = context.getString(R.string.hybrid_reader_night_text_brightness)
        max = 255 - PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS
        applyColors(palette.divider, palette.accent, palette.accent)
        onProgressChanged = { progress, fromUser ->
          if (fromUser) {
            controller?.setNightTextBrightness(PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS + progress)
            onPageAppearanceUpdated?.invoke()
          }
        }
      }
      bgBrightnessLabel.apply {
        text = context.getString(R.string.hybrid_reader_night_bg_brightness)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        appearanceLabels.add(this)
      }
      bgBrightnessSeek.apply {
        contentDescription = context.getString(R.string.hybrid_reader_night_bg_brightness)
        max = PageThemeConstants.MAX_NIGHT_MODE_BACKGROUND_BRIGHTNESS
        applyColors(palette.divider, palette.accent, palette.accent)
        onProgressChanged = { progress, fromUser ->
          if (fromUser) {
            controller?.setNightBackgroundBrightness(progress)
            onPageAppearanceUpdated?.invoke()
          }
        }
      }
      addView(textBrightnessLabel)
      addView(textBrightnessSeek)
      addView(
        bgBrightnessLabel,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6f) }
      )
      addView(bgBrightnessSeek)
    }
    col.addView(nightControlsBox)

    appearanceSheetScroll.addView(col)
    appearanceSheetCard.addView(
      appearanceSheetScroll,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    )

    val marginH = dp(STUDY_SHEET_HORIZONTAL_INSET_DP)
    addView(
      appearanceSheetCard,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        leftMargin = marginH
        rightMargin = marginH
        bottomMargin = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
      }
    )
    updateAppearanceSheetMaxHeight()
    applyAppearanceSheetPalette(palette)
  }

  fun bind(
    readerController: HybridReaderController,
    scope: CoroutineScope,
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
    onStop: (() -> Unit)? = null,
    onOledDarkModeChanged: ((Boolean) -> Unit)? = null
  ) {
    this.controller = readerController
    this.onBackToHub = onBackToHub
    this.onJumpPage = onJumpPage
    this.onTogglePageBookmark = onTogglePageBookmark
    this.onAyahListen = onAyahListen
    this.onAyahTafsir = onAyahTafsir
    this.onAyahBookmark = onAyahBookmark
    this.onAyahCopy = onAyahCopy
    this.onAyahShare = onAyahShare
    this.onPlayPause = onPlayPause
    this.onSkipNext = onSkipNext
    this.onSkipPrev = onSkipPrev
    this.onStop = onStop
    this.onSpeedChanged = onSpeedChanged
    this.onRepeatChanged = onRepeatChanged
    this.onShowQariList = onShowQariList
    this.onCancelDownload = onCancelDownload
    this.onAllowMobileDownload = onAllowMobileDownload
    this.onOpenTafsirManager = onOpenTafsirManager
    this.onTafsirStepAyah = onTafsirStepAyah
    this.onAppThemeStyleSelected = onAppThemeStyleSelected
    this.onAppThemeModeSelected = onAppThemeModeSelected
    this.onOledDarkModeChanged = onOledDarkModeChanged
    this.onPageAppearanceUpdated = onPageAppearanceUpdated
    this.onMushafNudgeChanged = onMushafNudgeChanged

    collectionJob?.cancel()
    collectionJob = scope.launch {
      readerController.uiState.collectLatest { uiState ->
        render(uiState)
      }
    }
  }

  fun applyWindowInsets(statusTop: Int, navBottom: Int) {
    statusTopPx = statusTop
    navBottomPx = navBottom
    topPillsRow.setPadding(dp(12f), statusTop + dp(8f), dp(12f), dp(4f))
    audioCapsuleOuter.setPadding(dp(12f), dp(8f), dp(12f), navBottom + dp(12f))
    val desiredBottom = navBottom + dp(STUDY_SHEET_BOTTOM_INSET_DP)
    if (isStudyDrawerBuilt) {
      val desiredHeight = computeStudyDrawerHeightPx()
      (studyDrawerSheet.layoutParams as? LayoutParams)?.let { lp ->
        if (lp.bottomMargin != desiredBottom || lp.height != desiredHeight) {
          lp.bottomMargin = desiredBottom
          lp.height = desiredHeight
          studyDrawerSheet.layoutParams = lp
        }
      }
    }
    if (isAppearanceSheetBuilt) {
      (appearanceSheetCard.layoutParams as? LayoutParams)?.let { lp ->
        if (lp.bottomMargin != desiredBottom) {
          lp.bottomMargin = desiredBottom
          appearanceSheetCard.layoutParams = lp
        }
      }
      updateAppearanceSheetMaxHeight()
    }
    if (isTafsirPickerBuilt) {
      (tafsirPickerSheet.layoutParams as? LayoutParams)?.let { lp ->
        if (lp.bottomMargin != desiredBottom) {
          lp.bottomMargin = desiredBottom
          tafsirPickerSheet.layoutParams = lp
        }
      }
      updateTafsirPickerMaxHeight()
    }
    if (isAyahBarBuilt && ayahBarWrapper.visibility == View.VISIBLE && state.selectedStart != null && !state.isAnySheetOpen) {
      val maxViewportY = height.takeIf { it > 0 }?.toFloat() ?: Float.POSITIVE_INFINITY
      val validBounds = state.ayahOverlayBounds?.takeIf {
        it.isValidForOverlay(minBottomPx = statusTopPx.toFloat(), maxTopPx = maxViewportY)
      }
      if (validBounds != null) {
        updateFloatingAyahBarPosition(validBounds)
      }
    }
  }

  fun render(uiState: HybridReaderUiState) {
    val prevStatus = state.playbackStatus
    state = uiState

    val isSystemDark =
      (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val darkUi = ThemeUtil.isDarkAppearance(uiState.appearanceState.appThemeMode, isSystemDark)
    val styleNow = uiState.appearanceState.appThemeStyle
    val oledNow = darkUi && uiState.appearanceState.isOledDarkMode
    if (lastResolvedStyle != styleNow || lastResolvedDarkUi != darkUi || lastResolvedOledUi != oledNow) {
      lastResolvedStyle = styleNow
      lastResolvedDarkUi = darkUi
      lastResolvedOledUi = oledNow
      val resolvedPalette = HybridViewPalette.from(styleNow, darkUi, oledNow)
      if (resolvedPalette != palette) {
        applyPalette(resolvedPalette)
      }
    }

    // Top Pills
    val showTopChrome = uiState.isChromeVisible
    HybridViewTokens.animateVisibilityTransition(topPillsRow, showTopChrome, translateYDp = -8f)
    if (lastSuraTitleText != uiState.suraName) {
      lastSuraTitleText = uiState.suraName
      suraTitleText.text = uiState.suraName
    }
    val nextPageText = context.getString(
      R.string.hybrid_search_page_pill,
      QuranUtils.getLocalizedNumber(uiState.currentPage)
    )
    if (lastPageNumberText != nextPageText) {
      lastPageNumberText = nextPageText
      pageNumberText.text = nextPageText
    }
    if (lastRenderedPageBookmarked != uiState.isCurrentPageBookmarked) {
      lastRenderedPageBookmarked = uiState.isCurrentPageBookmarked
      toolSaveIconView.setImageDrawable(
        if (uiState.isCurrentPageBookmarked) toolSaveFilledIcon else toolSaveOutlineIcon
      )
      toolSaveText.setTextColor(
        if (uiState.isCurrentPageBookmarked) palette.editorialAccent else palette.textPrimary
      )
    }

    // Floating Ayah Bar — NEVER show while Study/Appearance sheet is open or before valid on-screen bounds exist!
    val selStart = uiState.selectedStart
    val maxViewportY = height.takeIf { it > 0 }?.toFloat() ?: Float.POSITIVE_INFINITY
    val validBounds = uiState.ayahOverlayBounds?.takeIf {
      it.isValidForOverlay(minBottomPx = statusTopPx.toFloat(), maxTopPx = maxViewportY)
    }
    val showAyahBar = selStart != null && !uiState.isAnySheetOpen && validBounds != null
    if (showAyahBar && selStart != null && validBounds != null) {
      ensureFloatingAyahBarBuilt()
      val selEnd = uiState.selectedEnd
      val nextTag = if (selEnd != null && selEnd != selStart) {
        context.getString(
          R.string.hybrid_reader_selection_range_tag_format,
          uiState.selectedSuraName,
          QuranUtils.getLocalizedNumber(selStart.ayah),
          QuranUtils.getLocalizedNumber(selEnd.ayah)
        )
      } else {
        context.getString(
          R.string.hybrid_reader_selection_tag_format,
          uiState.selectedSuraName,
          QuranUtils.getLocalizedNumber(selStart.ayah)
        )
      }
      if (lastAyahTagText != nextTag) {
        lastAyahTagText = nextTag
        ayahSelectionTagPill.text = nextTag
      }
      if (lastRenderedAyahBookmarked != uiState.isSelectedAyahBookmarked) {
        lastRenderedAyahBookmarked = uiState.isSelectedAyahBookmarked
        ayahBookmarkImageView?.setImageDrawable(
          if (uiState.isSelectedAyahBookmarked) ayahBookmarkFilledIcon else ayahBookmarkOutlineIcon
        )
        ayahBookmarkLabelView?.setTextColor(
          if (uiState.isSelectedAyahBookmarked) palette.editorialAccent else palette.textPrimary
        )
      }
      // Anchor margins, gravity (TOP|LEFT), and caret BEFORE revealing ayahBarWrapper so it never flashes top-right.
      updateFloatingAyahBarPosition(validBounds)
      HybridViewTokens.animateVisibilityTransition(
        ayahBarWrapper,
        true,
        translateYDp = 6f,
        hiddenVisibility = View.INVISIBLE
      )
    } else if (isAyahBarBuilt) {
      HybridViewTokens.animateVisibilityTransition(
        ayahBarWrapper,
        false,
        translateYDp = 6f,
        hiddenVisibility = View.INVISIBLE
      )
    }

    // Audio Capsule
    val showCapsule = uiState.isChromeVisible && !uiState.isAnySheetOpen
    HybridViewTokens.animateVisibilityTransition(
      audioCapsuleOuter,
      showCapsule,
      translateYDp = 12f,
      hiddenVisibility = View.INVISIBLE
    )
    val nextQariTitle = uiState.qariShortName.ifBlank { context.getString(R.string.app_name) }
    if (lastQariTitleText != nextQariTitle) {
      lastQariTitleText = nextQariTitle
      capsuleQariTitle.text = nextQariTitle
    }
    val playingAyah = uiState.playbackAyah
    val idleAyah = uiState.selectedStart
    val idleFallbackAyah = uiState.tafsirState.ayah
      .takeIf { it > 0 && uiState.tafsirState.sura == uiState.suraNumber }
      ?: uiState.firstAyahOnPage.coerceAtLeast(1)
    val nextCapsuleMeta = if (playingAyah != null) {
      context.getString(
        R.string.hybrid_reader_capsule_meta_format,
        uiState.suraName,
        QuranUtils.getLocalizedNumber(playingAyah.sura),
        QuranUtils.getLocalizedNumber(playingAyah.ayah)
      )
    } else {
      context.getString(
        R.string.hybrid_reader_qari_subtitle_format,
        QuranUtils.getLocalizedNumber(idleAyah?.sura ?: uiState.suraNumber.coerceAtLeast(1)),
        QuranUtils.getLocalizedNumber(idleAyah?.ayah ?: idleFallbackAyah)
      )
    }
    if (lastCapsuleMetaText != nextCapsuleMeta) {
      lastCapsuleMetaText = nextCapsuleMeta
      capsuleMetaText.text = nextCapsuleMeta
    }
    val isPlaying = uiState.playbackStatus == PlaybackStatus.PLAYING
    if (lastRenderedCapsulePlaying != isPlaying) {
      lastRenderedCapsulePlaying = isPlaying
      capsulePlayBtn.contentDescription = context.getString(
        if (isPlaying) R.string.hybrid_reader_pause_cd else R.string.hybrid_reader_play_cd
      )
      capsulePlayBtn.setImageDrawable(if (isPlaying) capsulePauseIcon else capsulePlayIcon)
    }

    // Download / Network Prompt banner
    when {
      uiState.showNetworkPrompt -> {
        capsuleDownloadRow.visibility = View.VISIBLE
        capsuleDownloadText.text = context.getString(R.string.hybrid_reader_download_mobile_prompt)
        capsuleDownloadAction.text = context.getString(R.string.hybrid_reader_download_allow)
        capsuleDownloadAction.setOnClickListener { onAllowMobileDownload?.invoke() }
      }
      uiState.downloadProgress != null -> {
        capsuleDownloadRow.visibility = View.VISIBLE
        capsuleDownloadText.text = context.getString(R.string.hybrid_reader_downloading)
        capsuleDownloadAction.text = context.getString(R.string.hybrid_reader_download_cancel)
        capsuleDownloadAction.setOnClickListener { onCancelDownload?.invoke() }
      }
      else -> {
        capsuleDownloadRow.visibility = View.GONE
      }
    }

    // Strict Zero-Idle-Draw timer gating
    if (isPlaying && !progressTickActive) {
      progressTickActive = true
      lastPlayTickMs = android.os.SystemClock.uptimeMillis()
      progressHandler.postDelayed(progressRunnable, 250L)
    } else if (!isPlaying && progressTickActive) {
      progressTickActive = false
      progressHandler.removeCallbacks(progressRunnable)
    }
    if (prevStatus != uiState.playbackStatus && uiState.playbackStatus == null) {
      syntheticProgressFraction = 0f
      updateProgressFillWidth()
    }

    // Study Drawer (2-tab: Recitation | Tafsir)
    if (uiState.isStudyDrawerOpen) {
      val wasOpen = isStudyDrawerRenderVisible
      ensureStudyDrawerBuilt()
      studyDrawerScrim.visibility = View.VISIBLE
      studyDrawerScrim.isClickable = true
      renderDrawerTabs(uiState, animateHeight = wasOpen)
      animateStudyDrawerVisibility(shouldBeOpen = true)
    } else if (isStudyDrawerBuilt) {
      cancelPostOpenTabPrebuilds()
      if (isTafsirPickerVisible) {
        dismissTafsirPicker()
      }
      if (isTafsirExpanded) {
        resetTafsirExpandedImmediate()
      }
      studyDrawerScrim.isClickable = false
      studyDrawerScrim.visibility = View.INVISIBLE
      animateStudyDrawerVisibility(shouldBeOpen = false)
    }

    // Dedicated Standalone Detached Z4 Reader Appearance Sheet
    if (uiState.isAppearanceSheetOpen) {
      if (isTafsirPickerVisible) {
        dismissTafsirPicker()
      }
      ensureAppearanceSheetBuilt()
      appearanceSheetScrim.visibility = View.VISIBLE
      appearanceSheetScrim.isClickable = true
      renderAppearanceSheet(uiState)
      animateAppearanceSheetVisibility(shouldBeOpen = true)
    } else if (isAppearanceSheetBuilt) {
      appearanceSheetScrim.isClickable = false
      appearanceSheetScrim.visibility = View.INVISIBLE
      animateAppearanceSheetVisibility(shouldBeOpen = false)
    }

    val nudgePx = if (uiState.isStudyDrawerOpen && uiState.selectedStart != null) {
      -(computeStudyDrawerHeightPx(uiState.selectedStudyTab, isTafsirExpanded) * 0.25f)
    } else {
      0f
    }
    if (abs(nudgePx - lastNudgeSentPx) > 0.5f) {
      lastNudgeSentPx = nudgePx
      onMushafNudgeChanged?.invoke(nudgePx)
    }
  }

  private fun prewarmStudyDrawerVectorBitmaps() {
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.CHEVRON_DOWN, palette.textSecondary, dp(18f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.CHEVRON_UP, palette.accent, dp(15f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.INDEX, palette.accent, dp(13f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.CLOSE, palette.textSecondary, dp(14f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.PREV, palette.textPrimary, dp(18f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.NEXT, palette.textPrimary, dp(18f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.STOP, palette.textPrimary, dp(15f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.PREV, palette.accent, dp(16f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.NEXT, palette.accent, dp(16f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.PLAY, palette.onCta, dp(20f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.PAUSE, palette.onCta, dp(20f))
    VectorPathDrawable.prewarmBitmap(HybridArchitecturalIcons.CHECK, palette.onAccent, dp(13f))
  }

  fun cancelPendingWarmupsOnStop() {
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    cancelPostOpenTabPrebuilds()
  }

  private fun updateStudyDrawerAccessState(open: Boolean) {
    if (!isStudyDrawerBuilt) return
    val a11y = if (open) {
      View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    } else {
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val focusMode = if (open) {
      ViewGroup.FOCUS_AFTER_DESCENDANTS
    } else {
      ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    if (studyDrawerSheet.importantForAccessibility != a11y) {
      studyDrawerSheet.importantForAccessibility = a11y
    }
    if (studyDrawerSheet.descendantFocusability != focusMode) {
      studyDrawerSheet.descendantFocusability = focusMode
    }
    studyDrawerScrim.importantForAccessibility = a11y
  }

  private fun updateActiveDrawerTabView(active: View) {
    val changed = activeDrawerTabView !== active
    activeDrawerTabView = active
    val tabs = arrayOf<View?>(
      if (isRecitationTabBuilt) recitationTabRoot else null,
      if (isTafsirTabBuilt) tafsirTabRoot else null
    )
    for (child in tabs) {
      if (child == null) continue
      if (child.alpha != 1f) {
        child.alpha = 1f
      }
      val targetVisibility = if (child === active) View.VISIBLE else View.INVISIBLE
      if (child.visibility != targetVisibility) {
        child.visibility = targetVisibility
      }
      if (child === active) {
        if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
          child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (child is ViewGroup && child.descendantFocusability != ViewGroup.FOCUS_AFTER_DESCENDANTS) {
          child.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
      } else {
        if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
          child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (child is ViewGroup && child.descendantFocusability != ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
          child.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
      }
    }
    if (changed && ::studyDrawerTabHost.isInitialized) {
      studyDrawerTabHost.invalidate()
    }
  }

  private fun animateStudyDrawerVisibility(shouldBeOpen: Boolean) {
    if (!isStudyDrawerBuilt) return
    if (studyDrawerTargetOpen == shouldBeOpen &&
      (isStudyDrawerRenderVisible == shouldBeOpen || !shouldBeOpen)
    ) {
      return
    }
    studyDrawerTargetOpen = shouldBeOpen
    val animGen = ++studyDrawerAnimGen

    studyDrawerSheet.animate().setListener(null)
    studyDrawerSheet.animate().cancel()
    if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
      studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    studyDrawerSheet.alpha = 1f

    val animationsEnabled = AdaptiveMotionTokens.areAnimationsEnabled(context)
    val slideOffsetPx = dpF(36f)
    if (shouldBeOpen) {
      val wasHidden = !isStudyDrawerRenderVisible
      isStudyDrawerRenderVisible = true
      if (studyDrawerSheet.visibility != View.VISIBLE) {
        studyDrawerSheet.visibility = View.VISIBLE
      }
      updateStudyDrawerAccessState(open = true)
      if (wasHidden && isAttachedToWindow && animationsEnabled) {
        isStudyDrawerOpening = true
        studyDrawerSheet.translationY = slideOffsetPx
        studyDrawerSheet.doOnPreDraw {
          if (studyDrawerAnimGen == animGen &&
            studyDrawerTargetOpen == true &&
            isAttachedToWindow
          ) {
            if (studyDrawerSheet.layerType != View.LAYER_TYPE_HARDWARE) {
              studyDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            studyDrawerSheet.animate()
              .translationY(0f)
              .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
              .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
              .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                  isStudyDrawerOpening = false
                  if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
                    studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  studyDrawerSheet.animate().setListener(null)
                }

                override fun onAnimationEnd(animation: Animator) {
                  isStudyDrawerOpening = false
                  if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
                    studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  studyDrawerSheet.animate().setListener(null)
                  if (studyDrawerAnimGen == animGen && studyDrawerTargetOpen == true) {
                    if (isRecitationTabBuilt &&
                      state.selectedStudyTab == HybridReaderContract.StudyTab.RECITATION
                    ) {
                      renderRecitationTab(state)
                    }
                    schedulePostOpenTabPrebuilds()
                  }
                }
              })
              .start()
          }
        }
      } else {
        isStudyDrawerOpening = false
        studyDrawerSheet.translationY = 0f
        if (isRecitationTabBuilt &&
          state.selectedStudyTab == HybridReaderContract.StudyTab.RECITATION
        ) {
          renderRecitationTab(state)
        }
        schedulePostOpenTabPrebuilds()
      }
    } else {
      isStudyDrawerOpening = false
      updateStudyDrawerAccessState(open = false)
      if (isAttachedToWindow && isLaidOut && animationsEnabled && isStudyDrawerRenderVisible) {
        if (studyDrawerSheet.layerType != View.LAYER_TYPE_HARDWARE) {
          studyDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        studyDrawerSheet.animate()
          .translationY(slideOffsetPx)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
              if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
                studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              studyDrawerSheet.animate().setListener(null)
            }

            override fun onAnimationEnd(animation: Animator) {
              if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
                studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              studyDrawerSheet.animate().setListener(null)
              if (studyDrawerAnimGen == animGen && studyDrawerTargetOpen == false) {
                isStudyDrawerRenderVisible = false
                studyDrawerSheet.visibility = View.INVISIBLE
                studyDrawerSheet.translationY = 0f
              }
            }
          })
          .start()
      } else {
        isStudyDrawerRenderVisible = false
        studyDrawerSheet.visibility = View.INVISIBLE
        studyDrawerSheet.translationY = 0f
      }
    }
  }

  private fun snapStudyDrawerPendingState() {
    if (!isStudyDrawerBuilt) return
    isStudyDrawerOpening = false
    studyDrawerAnimGen++
    studyDrawerHeightAnimator?.cancel()
    studyDrawerHeightAnimator = null
    studyDrawerSheet.animate().setListener(null)
    studyDrawerSheet.animate().cancel()
    if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
      studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    if (studyDrawerTargetOpen != true) {
      isStudyDrawerRenderVisible = false
      studyDrawerSheet.visibility = View.INVISIBLE
    } else {
      isStudyDrawerRenderVisible = true
      studyDrawerSheet.visibility = View.VISIBLE
    }
    studyDrawerSheet.alpha = 1f
    studyDrawerSheet.translationY = 0f
  }

  private fun schedulePostOpenTabPrebuilds() {
    if (!isAttachedToWindow || !state.isStudyDrawerOpen) return
    // Strictly avoid loading Tafsir database when ONLY the Recitation tab is open;
    // only prebuild the lightweight Tafsir view shell after the open animation settles.
    if (isTafsirTabBuilt) return
    if (!postOpenPrebuildScheduled) {
      postOpenPrebuildScheduled = true
      postDelayed(prebuildTafsirRunnable, POST_OPEN_PREBUILD_START_DELAY_MS)
    }
  }

  private fun cancelPostOpenTabPrebuilds() {
    postOpenPrebuildScheduled = false
    removeCallbacks(prebuildTafsirRunnable)
  }

  private fun premeasureTabInDrawer(tabView: View) {
    if (!::studyDrawerTabHost.isInitialized) return
    val hostW = studyDrawerTabHost.width
    val hostH = studyDrawerTabHost.height
    if (hostW > 0 && hostH > 0) {
      tabView.measure(
        MeasureSpec.makeMeasureSpec(hostW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.EXACTLY)
      )
      tabView.layout(0, 0, hostW, hostH)
    }
  }

  private fun computeStudyDrawerHeightPx(
    tab: HybridReaderContract.StudyTab = state.selectedStudyTab,
    expanded: Boolean = isTafsirExpanded
  ): Int {
    val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val bottomMargin = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
    val maxSafeHeight = (vh - bottomMargin - statusTopPx - dp(16f)).coerceAtLeast(dp(236f))
    val normalizedTab = if (tab == HybridReaderContract.StudyTab.TAFSIR) {
      HybridReaderContract.StudyTab.TAFSIR
    } else {
      HybridReaderContract.StudyTab.RECITATION
    }
    return when {
      normalizedTab == HybridReaderContract.StudyTab.RECITATION -> {
        val hasDownloadRow = state.showNetworkPrompt || state.downloadProgress != null
        val compactTarget = if (hasDownloadRow) dp(268f) else dp(236f)
        compactTarget.coerceAtMost(maxSafeHeight)
      }
      expanded -> {
        val desired = (vh * 0.80f).roundToInt()
        val minExpanded = dp(380f).coerceAtMost(maxSafeHeight)
        desired.coerceIn(minExpanded, maxSafeHeight)
      }
      else -> {
        val desired = (vh * 0.42f).roundToInt()
        val minTafsir = dp(304f).coerceAtMost(maxSafeHeight)
        val maxCompact = (vh - bottomMargin - dp(240f)).coerceAtLeast(minTafsir).coerceAtMost(maxSafeHeight)
        desired.coerceIn(minTafsir, maxCompact)
      }
    }
  }

  private fun updateStudyDrawerHeight(
    tab: HybridReaderContract.StudyTab = state.selectedStudyTab,
    expanded: Boolean = isTafsirExpanded,
    animate: Boolean
  ) {
    if (!isStudyDrawerBuilt) return
    val targetHeight = computeStudyDrawerHeightPx(tab, expanded)
    val lp = studyDrawerSheet.layoutParams as? LayoutParams ?: return
    val currentHeight = lp.height.takeIf { it > 0 } ?: studyDrawerSheet.height.takeIf { it > 0 } ?: targetHeight
    if (currentHeight == targetHeight && studyDrawerHeightAnimator == null) {
      return
    }
    studyDrawerHeightAnimator?.cancel()
    studyDrawerHeightAnimator = null

    val animationsEnabled = AdaptiveMotionTokens.areAnimationsEnabled(context)
    if (animate && isAttachedToWindow && isStudyDrawerRenderVisible && animationsEnabled && currentHeight != targetHeight) {
      if (studyDrawerSheet.layerType != View.LAYER_TYPE_HARDWARE) {
        studyDrawerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
      }
      val anim = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
        duration = AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS
        interpolator = AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED
        addUpdateListener { va ->
          val nextH = va.animatedValue as Int
          val curLp = studyDrawerSheet.layoutParams as? LayoutParams
          if (curLp != null && curLp.height != nextH) {
            curLp.height = nextH
            studyDrawerSheet.layoutParams = curLp
          }
        }
        addListener(object : AnimatorListenerAdapter() {
          private var cancelled = false

          override fun onAnimationCancel(animation: Animator) {
            cancelled = true
            studyDrawerHeightAnimator = null
            if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
              studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
            }
          }

          override fun onAnimationEnd(animation: Animator) {
            studyDrawerHeightAnimator = null
            if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE) {
              studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            if (!cancelled && isTafsirTabBuilt && state.isStudyDrawerOpen &&
              state.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR
            ) {
              scheduleTafsirOverflowCheck()
            }
          }
        })
      }
      studyDrawerHeightAnimator = anim
      anim.start()
    } else {
      if (studyDrawerSheet.layerType != View.LAYER_TYPE_NONE && !isStudyDrawerOpening) {
        studyDrawerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
      }
      if (lp.height != targetHeight) {
        lp.height = targetHeight
        studyDrawerSheet.layoutParams = lp
      }
      if (isTafsirTabBuilt && state.isStudyDrawerOpen &&
        state.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR
      ) {
        scheduleTafsirOverflowCheck()
      }
    }
  }

  private fun toggleTafsirExpanded() {
    if (!isTafsirTabBuilt || !state.isStudyDrawerOpen ||
      state.selectedStudyTab != HybridReaderContract.StudyTab.TAFSIR
    ) {
      return
    }
    setTafsirExpanded(!isTafsirExpanded, animate = true)
  }

  private fun setTafsirExpanded(expanded: Boolean, animate: Boolean) {
    if (isTafsirExpanded == expanded) return
    isTafsirExpanded = expanded
    if (isTafsirTabBuilt) {
      tafsirExpandBtn.contentDescription = context.getString(
        if (expanded) R.string.hybrid_reader_tafsir_collapse_cd else R.string.hybrid_reader_tafsir_expand_cd
      )
      val targetRotation = if (expanded) 180f else 0f
      tafsirExpandIconView.animate().cancel()
      if (animate && isAttachedToWindow && AdaptiveMotionTokens.areAnimationsEnabled(context)) {
        tafsirExpandIconView.animate()
          .rotation(targetRotation)
          .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .start()
      } else {
        tafsirExpandIconView.rotation = targetRotation
      }
      updateTafsirOverflowControls(tafsirHasOverflow)
    }
    updateStudyDrawerHeight(HybridReaderContract.StudyTab.TAFSIR, expanded = expanded, animate = animate)
  }

  private fun resetTafsirExpandedImmediate() {
    isTafsirExpanded = false
    if (isTafsirTabBuilt) {
      tafsirExpandIconView.animate().cancel()
      tafsirExpandIconView.rotation = 0f
      tafsirExpandBtn.contentDescription = context.getString(R.string.hybrid_reader_tafsir_expand_cd)
      updateTafsirOverflowControls(tafsirHasOverflow)
    }
  }

  private fun scheduleTafsirOverflowCheck() {
    if (!isTafsirTabBuilt) return
    tafsirBodyScroll.removeCallbacks(tafsirOverflowCheckRunnable)
    tafsirBodyScroll.post(tafsirOverflowCheckRunnable)
  }

  private fun updateTafsirOverflowControls(overflows: Boolean) {
    tafsirHasOverflow = overflows
    if (!isTafsirTabBuilt) return
    val showToggle = overflows || isTafsirExpanded
    val targetBtnVis = if (showToggle) View.VISIBLE else View.GONE
    if (tafsirExpandBtn.visibility != targetBtnVis) {
      tafsirExpandBtn.visibility = targetBtnVis
    }
    val targetFadeVis = if (overflows && !isTafsirExpanded) View.VISIBLE else View.GONE
    if (tafsirOverflowFade.visibility != targetFadeVis) {
      tafsirOverflowFade.visibility = targetFadeVis
    }
    val targetBottomPad = if (showToggle) dp(36f) else dp(12f)
    if (tafsirBodyScroll.paddingBottom != targetBottomPad) {
      tafsirBodyScroll.setPadding(
        tafsirBodyScroll.paddingLeft,
        tafsirBodyScroll.paddingTop,
        tafsirBodyScroll.paddingRight,
        targetBottomPad
      )
    }
  }

  fun isTafsirPickerOpen(): Boolean = isTafsirPickerVisible

  fun dismissTafsirPicker(): Boolean {
    if (!isTafsirPickerBuilt || (!isTafsirPickerVisible && tafsirPickerTargetOpen != true)) {
      return false
    }
    tafsirPickerScrim.isClickable = false
    tafsirPickerScrim.visibility = View.INVISIBLE
    animateTafsirPickerVisibility(shouldBeOpen = false)
    return true
  }

  private fun openTafsirPicker() {
    ensureTafsirPickerBuilt()
    renderTafsirPickerList(state.tafsirState)
    tafsirPickerScrim.visibility = View.VISIBLE
    tafsirPickerScrim.isClickable = true
    animateTafsirPickerVisibility(shouldBeOpen = true)
  }

  private fun updateTafsirPickerMaxHeight() {
    if (!isTafsirPickerBuilt) return
    val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val maxScrollH = (vh * 0.42f).roundToInt().coerceAtLeast(dp(140f))
    tafsirPickerScroll.layoutParams = (tafsirPickerScroll.layoutParams as? LinearLayout.LayoutParams
      ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)).apply {
      height = LinearLayout.LayoutParams.WRAP_CONTENT
    }
    tafsirPickerScroll.minimumHeight = 0
    tafsirPickerListCol.minimumHeight = 0
    tafsirPickerScroll.post {
      if (isAttachedToWindow && isTafsirPickerBuilt && tafsirPickerListCol.height > maxScrollH) {
        (tafsirPickerScroll.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
          if (lp.height != maxScrollH) {
            lp.height = maxScrollH
            tafsirPickerScroll.layoutParams = lp
          }
        }
      }
    }
  }

  private fun renderTafsirPickerList(t: TafsirDisplayState) {
    if (!isTafsirPickerBuilt) return
    val rowRadius = dpF(14f)
    val strokePx = dp(1f)
    val selectedStrokePx = dp(1.5f)
    val checkPx = dp(15f)
    val sourcesChanged = tafsirPickerRows.size != t.availableSources.size ||
      t.availableSources.indices.any { idx ->
        val src = t.availableSources[idx]
        val holder = tafsirPickerRows[idx]
        holder.filename != src.filename || holder.label != src.displayName
      }
    if (sourcesChanged) {
      tafsirPickerRows.forEach { HybridViewTokens.resetApplePressFeedback(it.row) }
      tafsirPickerListCol.removeAllViews()
      tafsirPickerRows.clear()
      t.availableSources.forEachIndexed { idx, src ->
        val displayLabel = resolveLocalizedSourceTitle(src.filename, src.displayName)
        val bgDrawable = HybridViewTokens.roundedDrawable(Color.TRANSPARENT, radiusPx = rowRadius)
        val titleView = TextView(context).apply {
          text = displayLabel
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
          maxLines = 1
          ellipsize = TextUtils.TruncateAt.END
        }
        val checkIcon = HybridArchitecturalIcons.drawable(HybridArchitecturalIcons.CHECK, palette.accent, checkPx)
        val checkView = ImageView(context).apply {
          setImageDrawable(checkIcon)
          scaleType = ImageView.ScaleType.CENTER
          visibility = View.INVISIBLE
        }
        val row = LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          layoutDirection = View.LAYOUT_DIRECTION_RTL
          isClickable = true
          isFocusable = true
          minimumHeight = dp(44f)
          setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
          background = bgDrawable
          addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
          addView(checkView, LinearLayout.LayoutParams(checkPx, checkPx).apply { marginStart = dp(10f) })
          setOnClickListener {
            controller?.selectTafsirSource(src.filename)
            dismissTafsirPicker()
          }
          HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
        }
        tafsirPickerRows.add(
          TafsirPickerRowHolder(src.filename, src.displayName, row, titleView, checkView, checkIcon, bgDrawable)
        )
        tafsirPickerListCol.addView(
          row,
          LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (idx < t.availableSources.lastIndex) bottomMargin = dp(6f)
          }
        )
      }
    }
    val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
    tafsirPickerRows.forEach { holder ->
      val selected = holder.filename == t.selectedFilename
      holder.row.isSelected = selected
      holder.titleView.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
      holder.titleView.setTextColor(if (selected) palette.accent else palette.textPrimary)
      holder.checkIcon.setTintColor(palette.accent)
      holder.checkView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
      if (selected) {
        holder.bgDrawable.setColor(palette.accentSoftBg)
        holder.bgDrawable.setStroke(selectedStrokePx, palette.accent)
      } else {
        holder.bgDrawable.setColor(palette.surfaceVariant)
        holder.bgDrawable.setStroke(strokePx, hairline)
      }
    }
  }

  private fun animateTafsirPickerVisibility(shouldBeOpen: Boolean) {
    if (!isTafsirPickerBuilt) return
    if (tafsirPickerTargetOpen == shouldBeOpen &&
      (isTafsirPickerVisible == shouldBeOpen || !shouldBeOpen)
    ) {
      return
    }
    tafsirPickerTargetOpen = shouldBeOpen
    val animGen = ++tafsirPickerAnimGen

    tafsirPickerSheet.animate().setListener(null)
    tafsirPickerSheet.animate().cancel()
    if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
      tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    tafsirPickerSheet.alpha = 1f

    val animationsEnabled = AdaptiveMotionTokens.areAnimationsEnabled(context)
    val slideOffsetPx = dpF(32f)
    val a11y = if (shouldBeOpen) {
      View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    } else {
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val focusMode = if (shouldBeOpen) {
      ViewGroup.FOCUS_AFTER_DESCENDANTS
    } else {
      ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    tafsirPickerSheet.importantForAccessibility = a11y
    tafsirPickerSheet.descendantFocusability = focusMode
    tafsirPickerScrim.importantForAccessibility = a11y

    if (shouldBeOpen) {
      val wasHidden = !isTafsirPickerVisible
      isTafsirPickerVisible = true
      tafsirPickerSheet.visibility = View.VISIBLE
      if (wasHidden && isAttachedToWindow && animationsEnabled) {
        tafsirPickerSheet.translationY = slideOffsetPx
        tafsirPickerSheet.doOnPreDraw {
          if (tafsirPickerAnimGen == animGen && tafsirPickerTargetOpen == true && isAttachedToWindow) {
            if (tafsirPickerSheet.layerType != View.LAYER_TYPE_HARDWARE) {
              tafsirPickerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            tafsirPickerSheet.animate()
              .translationY(0f)
              .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
              .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
              .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                  if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
                    tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  tafsirPickerSheet.animate().setListener(null)
                }

                override fun onAnimationEnd(animation: Animator) {
                  if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
                    tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  tafsirPickerSheet.animate().setListener(null)
                }
              })
              .start()
          }
        }
      } else {
        tafsirPickerSheet.translationY = 0f
      }
    } else {
      if (isAttachedToWindow && isLaidOut && animationsEnabled && isTafsirPickerVisible) {
        if (tafsirPickerSheet.layerType != View.LAYER_TYPE_HARDWARE) {
          tafsirPickerSheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        tafsirPickerSheet.animate()
          .translationY(slideOffsetPx)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
              if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
                tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              tafsirPickerSheet.animate().setListener(null)
            }

            override fun onAnimationEnd(animation: Animator) {
              if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
                tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              tafsirPickerSheet.animate().setListener(null)
              if (tafsirPickerAnimGen == animGen && tafsirPickerTargetOpen == false) {
                isTafsirPickerVisible = false
                tafsirPickerSheet.visibility = View.INVISIBLE
                tafsirPickerSheet.translationY = 0f
              }
            }
          })
          .start()
      } else {
        isTafsirPickerVisible = false
        tafsirPickerSheet.visibility = View.INVISIBLE
        tafsirPickerSheet.translationY = 0f
      }
    }
  }

  private fun snapTafsirPickerPendingState() {
    if (!isTafsirPickerBuilt) return
    tafsirPickerAnimGen++
    tafsirPickerSheet.animate().setListener(null)
    tafsirPickerSheet.animate().cancel()
    if (tafsirPickerSheet.layerType != View.LAYER_TYPE_NONE) {
      tafsirPickerSheet.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    if (tafsirPickerTargetOpen != true) {
      isTafsirPickerVisible = false
      tafsirPickerSheet.visibility = View.INVISIBLE
    } else {
      isTafsirPickerVisible = true
      tafsirPickerSheet.visibility = View.VISIBLE
    }
    tafsirPickerSheet.alpha = 1f
    tafsirPickerSheet.translationY = 0f
  }

  private fun updateAppearanceSheetAccessState(open: Boolean) {
    if (!isAppearanceSheetBuilt) return
    val a11y = if (open) {
      View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    } else {
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val focusMode = if (open) {
      ViewGroup.FOCUS_AFTER_DESCENDANTS
    } else {
      ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    if (appearanceSheetCard.importantForAccessibility != a11y) {
      appearanceSheetCard.importantForAccessibility = a11y
    }
    if (appearanceSheetCard.descendantFocusability != focusMode) {
      appearanceSheetCard.descendantFocusability = focusMode
    }
    appearanceSheetScrim.importantForAccessibility = a11y
  }

  private fun updateAppearanceSheetMaxHeight() {
    if (!isAppearanceSheetBuilt) return
    val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val bottomMargin = navBottomPx + dp(STUDY_SHEET_BOTTOM_INSET_DP)
    val maxScrollH = ((vh - bottomMargin - statusTopPx - dp(96f)).coerceAtLeast(dp(220f)))
      .coerceAtMost((vh * 0.62f).roundToInt())
    appearanceSheetScroll.post {
      if (!isAttachedToWindow || !isAppearanceSheetBuilt) return@post
      val contentH = appearanceSheetScroll.getChildAt(0)?.measuredHeight ?: 0
      val lp = appearanceSheetScroll.layoutParams as? LinearLayout.LayoutParams ?: return@post
      val targetH = if (contentH > maxScrollH) maxScrollH else LinearLayout.LayoutParams.WRAP_CONTENT
      if (lp.height != targetH) {
        lp.height = targetH
        appearanceSheetScroll.layoutParams = lp
      }
    }
  }

  private fun animateAppearanceSheetVisibility(shouldBeOpen: Boolean) {
    if (!isAppearanceSheetBuilt) return
    if (appearanceSheetTargetOpen == shouldBeOpen &&
      (isAppearanceSheetRenderVisible == shouldBeOpen || !shouldBeOpen)
    ) {
      return
    }
    appearanceSheetTargetOpen = shouldBeOpen
    val animGen = ++appearanceSheetAnimGen

    appearanceSheetCard.animate().setListener(null)
    appearanceSheetCard.animate().cancel()
    if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
      appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    appearanceSheetCard.alpha = 1f

    val animationsEnabled = AdaptiveMotionTokens.areAnimationsEnabled(context)
    val slideOffsetPx = dpF(36f)
    if (shouldBeOpen) {
      val wasHidden = !isAppearanceSheetRenderVisible
      isAppearanceSheetRenderVisible = true
      if (appearanceSheetCard.visibility != View.VISIBLE) {
        appearanceSheetCard.visibility = View.VISIBLE
      }
      updateAppearanceSheetAccessState(open = true)
      if (wasHidden && isAttachedToWindow && animationsEnabled) {
        appearanceSheetCard.translationY = slideOffsetPx
        appearanceSheetCard.doOnPreDraw {
          if (appearanceSheetAnimGen == animGen &&
            appearanceSheetTargetOpen == true &&
            isAttachedToWindow
          ) {
            if (appearanceSheetCard.layerType != View.LAYER_TYPE_HARDWARE) {
              appearanceSheetCard.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            appearanceSheetCard.animate()
              .translationY(0f)
              .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
              .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
              .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                  if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
                    appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  appearanceSheetCard.animate().setListener(null)
                }

                override fun onAnimationEnd(animation: Animator) {
                  if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
                    appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
                  }
                  appearanceSheetCard.animate().setListener(null)
                }
              })
              .start()
          }
        }
      } else {
        appearanceSheetCard.translationY = 0f
      }
    } else {
      updateAppearanceSheetAccessState(open = false)
      if (isAttachedToWindow && isLaidOut && animationsEnabled && isAppearanceSheetRenderVisible) {
        if (appearanceSheetCard.layerType != View.LAYER_TYPE_HARDWARE) {
          appearanceSheetCard.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        appearanceSheetCard.animate()
          .translationY(slideOffsetPx)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
              if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
                appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              appearanceSheetCard.animate().setListener(null)
            }

            override fun onAnimationEnd(animation: Animator) {
              if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
                appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
              }
              appearanceSheetCard.animate().setListener(null)
              if (appearanceSheetAnimGen == animGen && appearanceSheetTargetOpen == false) {
                isAppearanceSheetRenderVisible = false
                appearanceSheetCard.visibility = View.INVISIBLE
                appearanceSheetCard.translationY = 0f
              }
            }
          })
          .start()
      } else {
        isAppearanceSheetRenderVisible = false
        appearanceSheetCard.visibility = View.INVISIBLE
        appearanceSheetCard.translationY = 0f
      }
    }
  }

  private fun snapAppearanceSheetPendingState() {
    if (!isAppearanceSheetBuilt) return
    appearanceSheetAnimGen++
    appearanceSheetCard.animate().setListener(null)
    appearanceSheetCard.animate().cancel()
    if (appearanceSheetCard.layerType != View.LAYER_TYPE_NONE) {
      appearanceSheetCard.setLayerType(View.LAYER_TYPE_NONE, null)
    }
    if (appearanceSheetTargetOpen != true) {
      isAppearanceSheetRenderVisible = false
      appearanceSheetCard.visibility = View.INVISIBLE
    } else {
      isAppearanceSheetRenderVisible = true
      appearanceSheetCard.visibility = View.VISIBLE
    }
    appearanceSheetCard.alpha = 1f
    appearanceSheetCard.translationY = 0f
  }

  private fun renderDrawerTabs(uiState: HybridReaderUiState, animateHeight: Boolean = false) {
    val activeTab = if (uiState.selectedStudyTab == HybridReaderContract.StudyTab.TAFSIR) {
      HybridReaderContract.StudyTab.TAFSIR
    } else {
      HybridReaderContract.StudyTab.RECITATION
    }
    val tabChanged = lastRenderedDrawerTab != activeTab
    if (tabChanged && activeTab != HybridReaderContract.StudyTab.TAFSIR) {
      if (isTafsirPickerVisible) {
        dismissTafsirPicker()
      }
      if (isTafsirExpanded) {
        resetTafsirExpandedImmediate()
      }
    }
    if (tabChanged || lastRenderedDrawerPalette != palette) {
      val paletteChanged = lastRenderedDrawerPalette != palette
      lastRenderedDrawerTab = activeTab
      lastRenderedDrawerPalette = palette
      val activeElevationPx = dpF(HybridViewTokens.zLevelElevationDp(2, palette.isDark))
      drawerTabButtons.forEach { (tab, tv) ->
        val selected = tab == activeTab
        tv.isSelected = selected
        tv.setTextColor(if (selected) palette.accent else palette.textSecondary)
        drawerTabThumbDrawables[tab]?.alpha = if (selected) 255 else 0
        tv.elevation = if (selected) activeElevationPx else 0f
        if (paletteChanged) {
          drawerTabPressDrawables[tab]?.setPressColor(palette.ripple)
        }
      }
    }

    when (activeTab) {
      HybridReaderContract.StudyTab.RECITATION -> ensureRecitationTabBuilt()
      HybridReaderContract.StudyTab.TAFSIR -> ensureTafsirTabBuilt()
      HybridReaderContract.StudyTab.APPEARANCE -> ensureRecitationTabBuilt()
    }

    val activeTabView: View = if (activeTab == HybridReaderContract.StudyTab.TAFSIR) {
      tafsirTabRoot
    } else {
      recitationTabRoot
    }
    updateActiveDrawerTabView(activeTabView)

    if (activeTab == HybridReaderContract.StudyTab.TAFSIR) {
      renderTafsirTab(uiState)
    } else {
      renderRecitationTab(uiState)
    }

    updateStudyDrawerHeight(
      tab = activeTab,
      expanded = activeTab == HybridReaderContract.StudyTab.TAFSIR && isTafsirExpanded,
      animate = animateHeight && tabChanged
    )
  }

  private fun resolveLocalizedSourceTitle(filename: String?, rawName: String): String {
    if (rawName.isBlank()) {
      return context.getString(R.string.hybrid_reader_tafsir_muyassar)
    }
    if (!TafsirLookupHelper.hasArabicScript(rawName)) {
      TafsirLookupHelper.localizedArabicTafsirTitle(filename = filename, name = rawName)
        ?.let { return it }
    }
    return rawName
  }

  private fun renderRecitationTab(uiState: HybridReaderUiState) {
    val qariName = uiState.qariFullName.ifBlank { context.getString(R.string.app_name) }
    if (recQariNameText.text != qariName) {
      recQariNameText.text = qariName
    }
    val targetAyah = uiState.playbackAyah ?: uiState.selectedStart
    val fallbackAyah = uiState.firstAyahOnPage.coerceAtLeast(1)
    val qariSub = context.getString(
      R.string.hybrid_reader_qari_subtitle_format,
      QuranUtils.getLocalizedNumber(targetAyah?.sura ?: uiState.suraNumber.coerceAtLeast(1)),
      QuranUtils.getLocalizedNumber(targetAyah?.ayah ?: fallbackAyah)
    )
    if (recQariSubtitleText.text != qariSub) {
      recQariSubtitleText.text = qariSub
    }
    when {
      uiState.showNetworkPrompt -> {
        recDownloadRow.visibility = View.VISIBLE
        recDownloadText.text = context.getString(R.string.hybrid_reader_download_mobile_prompt)
        recDownloadAction.text = context.getString(R.string.hybrid_reader_download_allow)
        recDownloadAction.setOnClickListener { onAllowMobileDownload?.invoke() }
      }
      uiState.downloadProgress != null -> {
        recDownloadRow.visibility = View.VISIBLE
        recDownloadText.text = context.getString(R.string.hybrid_reader_downloading)
        recDownloadAction.text = context.getString(R.string.hybrid_reader_download_cancel)
        recDownloadAction.setOnClickListener { onCancelDownload?.invoke() }
      }
      else -> {
        recDownloadRow.visibility = View.GONE
      }
    }
    val isPlaying = uiState.playbackStatus == PlaybackStatus.PLAYING
    if (lastRenderedRecPlaying != isPlaying) {
      lastRenderedRecPlaying = isPlaying
      recPlayBtn.setImageDrawable(if (isPlaying) recPauseIcon else recPlayIcon)
      recPlayBtn.contentDescription = context.getString(
        if (isPlaying) R.string.hybrid_reader_pause_cd else R.string.hybrid_reader_play_cd
      )
    }
    val audioActive = uiState.isAudioActive
    val targetStopVis = if (audioActive) View.VISIBLE else View.GONE
    if (recStopBtn.visibility != targetStopVis) {
      recStopBtn.visibility = targetStopVis
    }
    if (recStopBtn.isEnabled != audioActive) {
      recStopBtn.isEnabled = audioActive
    }
    val speedLabel = context.getString(
      R.string.hybrid_reader_speed_format,
      if (uiState.playbackSpeed % 1f == 0f) {
        uiState.playbackSpeed.toInt().toString()
      } else {
        uiState.playbackSpeed.toString()
      }
    )
    if (recSpeedBtn.text != speedLabel) {
      recSpeedBtn.text = speedLabel
    }
    val repeatLabel = when (uiState.repeatCount) {
      0 -> context.getString(R.string.hybrid_reader_repeat)
      -1 -> context.getString(R.string.hybrid_reader_repeat_infinite)
      else -> context.getString(
        R.string.hybrid_reader_repeat_count_format,
        QuranUtils.getLocalizedNumber(uiState.repeatCount)
      )
    }
    if (recRepeatBtn.text != repeatLabel) {
      recRepeatBtn.text = repeatLabel
    }
  }

  private fun renderTafsirTab(uiState: HybridReaderUiState) {
    val t = uiState.tafsirState
    val titleText = context.getString(
      R.string.hybrid_reader_tafsir_peek_title_format,
      t.suraName.ifBlank { uiState.suraName },
      QuranUtils.getLocalizedNumber(t.ayah.coerceAtLeast(1))
    )
    if (tafsirAyahTitle.text != titleText) {
      tafsirAyahTitle.text = titleText
    }

    val targetSourceVis = if (t.availableSources.isEmpty()) View.GONE else View.VISIBLE
    if (tafsirSourceScroll.visibility != targetSourceVis) {
      tafsirSourceScroll.visibility = targetSourceVis
    }

    val sourcesChanged = tafsirChips.size != t.availableSources.size ||
      t.availableSources.indices.any { idx ->
        val src = t.availableSources[idx]
        val holder = tafsirChips[idx]
        holder.filename != src.filename || holder.label != src.displayName
      }
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)

    if (sourcesChanged) {
      tafsirSourceRow.removeAllViews()
      tafsirChips.clear()
      lastTafsirSelectedFilename = null
      lastTafsirChipPalette = null
      t.availableSources.forEach { src ->
        val displayLabel = resolveLocalizedSourceTitle(src.filename, src.displayName)
        val bgDrawable = HybridViewTokens.roundedDrawable(
          fillColor = Color.TRANSPARENT,
          radiusPx = pillRadius
        )
        val chip = TextView(context).apply {
          text = displayLabel
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
          typeface = Typeface.DEFAULT
          isClickable = true
          setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
          background = bgDrawable
          setOnClickListener { controller?.selectTafsirSource(src.filename) }
        }
        tafsirChips.add(TafsirChipHolder(src.filename, src.displayName, chip, bgDrawable))
        tafsirSourceRow.addView(
          chip,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { marginEnd = dp(6f) }
        )
      }
    }

    if (sourcesChanged || lastTafsirSelectedFilename != t.selectedFilename || lastTafsirChipPalette != palette) {
      lastTafsirSelectedFilename = t.selectedFilename
      lastTafsirChipPalette = palette
      val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
      tafsirChips.forEach { holder ->
        val selected = holder.filename == t.selectedFilename
        holder.view.setTextColor(if (selected) palette.onCta else palette.textPrimary)
        if (selected) {
          holder.bgDrawable.setColor(palette.ctaBackground)
          holder.bgDrawable.setStroke(0, Color.TRANSPARENT)
        } else {
          holder.bgDrawable.setColor(palette.surfaceVariant)
          holder.bgDrawable.setStroke(strokePx, hairline)
        }
      }
    }

    if (isTafsirPickerVisible) {
      renderTafsirPickerList(t)
    }

    if (t.loadState == TafsirLoadState.EMPTY) {
      tafsirBodyCard.visibility = View.GONE
      tafsirEmptyCard.visibility = View.VISIBLE
      if (isTafsirExpanded) {
        resetTafsirExpandedImmediate()
      }
      updateTafsirOverflowControls(false)
    } else {
      tafsirEmptyCard.visibility = View.GONE
      tafsirBodyCard.visibility = View.VISIBLE
      val bodyText = when (t.loadState) {
        TafsirLoadState.LOADING -> context.getString(R.string.hybrid_reader_tafsir_loading)
        else -> t.fullText.ifBlank { context.getString(R.string.hybrid_reader_tafsir_unavailable_ayah) }
      }
      val textChanged = tafsirBodyText.text != bodyText
      if (textChanged) {
        tafsirBodyText.text = bodyText
        tafsirBodyScroll.scrollTo(0, 0)
      }
      scheduleTafsirOverflowCheck()
    }
  }

  private fun renderAppearanceSheet(uiState: HybridReaderUiState) {
    val a = uiState.appearanceState
    val strokePx = dp(1f)
    val paletteChanged = lastAppearancePalette != palette

    if (paletteChanged || lastAppearanceMode != a.appThemeMode) {
      lastAppearanceMode = a.appThemeMode
      val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
      appModeButtons.forEach { (mode, tv) ->
        val selected = mode == a.appThemeMode
        tv.isSelected = selected
        tv.setTextColor(if (selected) palette.onCta else palette.textPrimary)
        val bgDrawable = appModeBgs.getValue(mode)
        if (selected) {
          bgDrawable.setColor(palette.ctaBackground)
          bgDrawable.setStroke(0, Color.TRANSPARENT)
        } else {
          bgDrawable.setColor(palette.surfaceVariant)
          bgDrawable.setStroke(strokePx, hairline)
        }
      }
    }

    if (paletteChanged || lastAppearanceStyle != a.appThemeStyle) {
      lastAppearanceStyle = a.appThemeStyle
      val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
      val selectedStrokePx = dp(1.5f)
      appStyleChips.forEach { (style, pair) ->
        val (chip, label) = pair
        val selected = style == a.appThemeStyle
        chip.isSelected = selected
        label.setTextColor(if (selected) palette.accent else palette.textPrimary)
        appStyleCheckIcons[style]?.setTintColor(palette.accent)
        appStyleCheckViews[style]?.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        val bgDrawable = appStyleBgs.getValue(style)
        if (selected) {
          bgDrawable.setColor(palette.accentSoftBg)
          bgDrawable.setStroke(selectedStrokePx, palette.accent)
        } else {
          bgDrawable.setColor(palette.surfaceVariant)
          bgDrawable.setStroke(strokePx, hairline)
        }
        val sp = HybridViewPalette.from(style, palette.isDark, palette.isOled)
        val ringColor = if (selected) {
          ColorUtils.compositeColors(palette.accentSoftBg, palette.cardSurface)
        } else {
          palette.surfaceVariant
        }
        appStyleSwatchBgs[style]?.setColors(sp.accent, sp.editorialAccent, ringColor)
      }
    }

    val activePageColor = when {
      a.isNightPage -> PageColor.Dark
      a.pageTheme == PageThemeConstants.PAGE_THEME_SEPIA -> PageColor.Sepia
      else -> PageColor.Light
    }
    if (paletteChanged || lastAppearancePageColor != activePageColor) {
      lastAppearancePageColor = activePageColor
      val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
      pageColorButtons.forEach { (pc, tv) ->
        val selected = pc == activePageColor
        tv.isSelected = selected
        tv.setTextColor(if (selected) palette.onCta else palette.textPrimary)
        val bgDrawable = pageColorBgs.getValue(pc)
        if (selected) {
          bgDrawable.setColor(palette.ctaBackground)
          bgDrawable.setStroke(0, Color.TRANSPARENT)
        } else {
          bgDrawable.setColor(palette.surfaceVariant)
          bgDrawable.setStroke(strokePx, hairline)
        }
      }
    }

    lastAppearancePalette = palette

    isSyncingAppearanceControls = true
    val wantOledVis = if (palette.isDark) View.VISIBLE else View.GONE
    if (oledToggleRow.visibility != wantOledVis) {
      oledToggleRow.visibility = wantOledVis
      updateAppearanceSheetMaxHeight()
    }
    if (paletteChanged || isOledToggleChecked != a.isOledDarkMode) {
      updateOledToggleVisual(a.isOledDarkMode)
    }
    val wantChecked = a.pageAppearance == PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    if (paletteChanged || isMatchAppChecked != wantChecked) {
      updateMatchAppToggleVisual(wantChecked)
    }
    val wantNightVis = if (a.isNightPage) View.VISIBLE else View.GONE
    if (nightControlsBox.visibility != wantNightVis) {
      nightControlsBox.visibility = wantNightVis
      updateAppearanceSheetMaxHeight()
    }
    if (a.isNightPage) {
      val targetTextProg = (a.textBrightness - PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS)
        .coerceIn(0, textBrightnessSeek.max)
      if (textBrightnessSeek.progress != targetTextProg) {
        textBrightnessSeek.progress = targetTextProg
      }
      val targetBgProg = a.backgroundBrightness.coerceIn(0, bgBrightnessSeek.max)
      if (bgBrightnessSeek.progress != targetBgProg) {
        bgBrightnessSeek.progress = targetBgProg
      }
    }
    isSyncingAppearanceControls = false
  }

  private fun reorderAyahBarChildren(direction: AyahBarPlacementDirection) {
    if (lastAyahBarDirection == direction) return
    lastAyahBarDirection = direction
    val caretW = dp(14f)
    val caretH = dp(7f)
    ayahBarWrapper.removeAllViews()
    if (direction == AyahBarPlacementDirection.BELOW_AYAH) {
      ayahBarWrapper.addView(ayahCaretTop, LinearLayout.LayoutParams(caretW, caretH))
      ayahBarWrapper.addView(
        ayahActionPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
      ayahBarWrapper.addView(
        ayahSelectionTagPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6f) }
      )
      ayahBarWrapper.addView(ayahCaretBottom, LinearLayout.LayoutParams(caretW, caretH))
    } else {
      ayahBarWrapper.addView(ayahCaretTop, LinearLayout.LayoutParams(caretW, caretH))
      ayahBarWrapper.addView(
        ayahSelectionTagPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6f) }
      )
      ayahBarWrapper.addView(
        ayahActionPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
      ayahBarWrapper.addView(ayahCaretBottom, LinearLayout.LayoutParams(caretW, caretH))
    }
  }

  private fun applyAyahPillFitMode(mode: Int, maxBarW: Int) {
    if (appliedFitMode == mode && (mode != 2 || lastAppliedMaxBarW == maxBarW)) return
    appliedFitMode = mode
    lastAppliedMaxBarW = maxBarW
    val pillPadV = dp(6f)
    val btnPadV = dp(4f)
    when (mode) {
      0 -> {
        val defaultPillPadH = dp(8f)
        val defaultBtnPadH = dp(9f)
        ayahActionPill.setPadding(defaultPillPadH, pillPadV, defaultPillPadH, pillPadV)
        (ayahActionPill.layoutParams as? LinearLayout.LayoutParams)?.let { pillLp ->
          pillLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
          pillLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
          ayahActionPill.layoutParams = pillLp
        }
        for (i in 0 until ayahActionButtons.size) {
          val btn = ayahActionButtons[i]
          btn.setPadding(defaultBtnPadH, btnPadV, defaultBtnPadH, btnPadV)
          (btn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
            btn.layoutParams = lp
          }
        }
      }
      1 -> {
        val compactPillPadH = dp(4f)
        val compactBtnPadH = dp(4f)
        ayahActionPill.setPadding(compactPillPadH, pillPadV, compactPillPadH, pillPadV)
        (ayahActionPill.layoutParams as? LinearLayout.LayoutParams)?.let { pillLp ->
          pillLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
          pillLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
          ayahActionPill.layoutParams = pillLp
        }
        for (i in 0 until ayahActionButtons.size) {
          val btn = ayahActionButtons[i]
          btn.setPadding(compactBtnPadH, btnPadV, compactBtnPadH, btnPadV)
          (btn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
            btn.layoutParams = lp
          }
        }
      }
      else -> {
        val tightBtnPadH = dp(2f)
        ayahActionPill.setPadding(dp(4f), pillPadV, dp(4f), pillPadV)
        (ayahActionPill.layoutParams as? LinearLayout.LayoutParams)?.let { pillLp ->
          pillLp.width = maxBarW
          pillLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
          ayahActionPill.layoutParams = pillLp
        }
        for (i in 0 until ayahActionButtons.size) {
          val btn = ayahActionButtons[i]
          btn.setPadding(tightBtnPadH, btnPadV, tightBtnPadH, btnPadV)
          (btn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = 0
            lp.weight = 1f
            btn.layoutParams = lp
          }
          ayahActionTexts.getOrNull(i)?.ellipsize = TextUtils.TruncateAt.END
        }
      }
    }
  }

  private fun prewarmFloatingAyahBarMeasure() {
    if (!isAyahBarBuilt) return
    val vw = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val maxBarW = (vw - dp(24f)).coerceAtLeast(dp(200f))
    val cfg = resources.configuration
    val dm = resources.displayMetrics
    val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      cfg.locales.toLanguageTags()
    } else {
      @Suppress("DEPRECATION")
      cfg.locale?.toLanguageTag().orEmpty()
    }
    val fitCacheKey = "$vw|${dm.density}|${cfg.fontScale}|$locales"
    if (lastFitCacheKey != fitCacheKey || lastFitMode < 0) {
      val unspecW = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
      val atMostH = MeasureSpec.makeMeasureSpec(vh, MeasureSpec.AT_MOST)
      applyAyahPillFitMode(0, maxBarW)
      ayahActionPill.measure(unspecW, atMostH)
      var resolvedMode = 0
      if (ayahActionPill.measuredWidth > maxBarW) {
        applyAyahPillFitMode(1, maxBarW)
        ayahActionPill.measure(unspecW, atMostH)
        resolvedMode = 1
        if (ayahActionPill.measuredWidth > maxBarW) {
          applyAyahPillFitMode(2, maxBarW)
          ayahActionPill.measure(MeasureSpec.makeMeasureSpec(maxBarW, MeasureSpec.EXACTLY), atMostH)
          resolvedMode = 2
        }
      }
      lastFitCacheKey = fitCacheKey
      lastFitMode = resolvedMode
    }
    ayahSelectionTagPill.measure(
      MeasureSpec.makeMeasureSpec(maxBarW, MeasureSpec.AT_MOST),
      MeasureSpec.makeMeasureSpec(vh, MeasureSpec.AT_MOST)
    )
  }

  private fun updateFloatingAyahBarPosition(targetBounds: AyahOverlayBounds? = state.ayahOverlayBounds) {
    if (!isAyahBarBuilt) return
    val maxViewportY = height.takeIf { it > 0 }?.toFloat() ?: Float.POSITIVE_INFINITY
    val bounds = targetBounds?.takeIf {
      it.isValidForOverlay(minBottomPx = statusTopPx.toFloat(), maxTopPx = maxViewportY)
    } ?: return
    val vw = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val vh = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val maxBarW = (vw - dp(24f)).coerceAtLeast(dp(200f))

    val cfg = resources.configuration
    val dm = resources.displayMetrics
    val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      cfg.locales.toLanguageTags()
    } else {
      @Suppress("DEPRECATION")
      cfg.locale?.toLanguageTag().orEmpty()
    }
    val fitCacheKey = "$vw|${dm.density}|${cfg.fontScale}|$locales"
    val unspecW = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    val atMostH = MeasureSpec.makeMeasureSpec(vh, MeasureSpec.AT_MOST)

    val cachedMode = if (lastFitCacheKey == fitCacheKey) lastFitMode else -1
    if (cachedMode >= 0) {
      applyAyahPillFitMode(cachedMode, maxBarW)
      if (cachedMode == 2) {
        ayahActionPill.measure(MeasureSpec.makeMeasureSpec(maxBarW, MeasureSpec.EXACTLY), atMostH)
      } else {
        ayahActionPill.measure(unspecW, atMostH)
      }
    } else {
      applyAyahPillFitMode(0, maxBarW)
      ayahActionPill.measure(unspecW, atMostH)
      var resolvedMode = 0
      if (ayahActionPill.measuredWidth > maxBarW) {
        applyAyahPillFitMode(1, maxBarW)
        ayahActionPill.measure(unspecW, atMostH)
        resolvedMode = 1
        if (ayahActionPill.measuredWidth > maxBarW) {
          applyAyahPillFitMode(2, maxBarW)
          ayahActionPill.measure(MeasureSpec.makeMeasureSpec(maxBarW, MeasureSpec.EXACTLY), atMostH)
          resolvedMode = 2
        }
      }
      lastFitCacheKey = fitCacheKey
      lastFitMode = resolvedMode
    }

    val wrapperWSpec = MeasureSpec.makeMeasureSpec(maxBarW, MeasureSpec.AT_MOST)
    val wrapperHSpec = MeasureSpec.makeMeasureSpec(vh, MeasureSpec.AT_MOST)
    ayahSelectionTagPill.visibility = View.VISIBLE
    ayahSelectionTagPill.measure(wrapperWSpec, wrapperHSpec)

    val pillH = ayahActionPill.measuredHeight.takeIf { it > 0 }?.toFloat() ?: dpF(52f)
    val tagH = (ayahSelectionTagPill.measuredHeight + dp(6f)).toFloat()

    val topObstacleBottomPx = if (topPillsRow.visibility == View.VISIBLE && topPillsRow.bottom > 0) {
      topPillsRow.bottom + dpF(4f)
    } else {
      statusTopPx + dpF(50f)
    }
    val bottomObstacleTopPx = if (audioCapsuleOuter.visibility == View.VISIBLE && audioCapsuleOuter.top > 0) {
      (audioCapsuleOuter.top + audioCapsuleOuter.paddingTop - dpF(2f))
    } else {
      (vh - navBottomPx - dpF(80f))
    }

    val placement = computeFloatingAyahBarPlacement(
      bounds = bounds,
      viewportWidthPx = vw.toFloat(),
      viewportHeightPx = vh.toFloat(),
      topObstacleBottomPx = topObstacleBottomPx,
      bottomObstacleTopPx = bottomObstacleTopPx,
      pillHorizontalInsetPx = dpF(12f),
      caretHeightPx = dpF(7f),
      pillHeightPx = pillH,
      selectionTagHeightPx = tagH,
      gapFromAyahPx = dpF(4f),
      caretHorizontalPaddingPx = dpF(24f)
    )

    val isBelow = placement.direction == AyahBarPlacementDirection.BELOW_AYAH
    ayahSelectionTagPill.visibility = if (placement.showSelectionTag) View.VISIBLE else View.GONE
    ayahCaretTop.visibility = if (isBelow) View.VISIBLE else View.GONE
    ayahCaretBottom.visibility = if (isBelow) View.GONE else View.VISIBLE
    reorderAyahBarChildren(placement.direction)
    ayahBarWrapper.measure(wrapperWSpec, wrapperHSpec)

    val barW = ayahBarWrapper.measuredWidth
    val barH = ayahBarWrapper.measuredHeight
    val targetTop = placement.topPx.roundToInt().coerceIn(0, (vh - barH).coerceAtLeast(0))

    val anchorMidX = if (isBelow) {
      (bounds.lastLineLeft + bounds.lastLineRight) * 0.5f
    } else {
      (bounds.firstLineLeft + bounds.firstLineRight) * 0.5f
    }
    val minLeft = dp(12f)
    val maxLeft = (vw - barW - dp(12f)).coerceAtLeast(minLeft)
    val targetLeft = (anchorMidX - barW * 0.5f)
      .roundToInt()
      .coerceIn(minLeft, maxLeft)

    val caretMaxOffset = (barW * 0.5f - dpF(24f)).coerceAtLeast(0f)
    val caretOffsetX = (anchorMidX - (targetLeft + barW * 0.5f))
      .coerceIn(-caretMaxOffset, caretMaxOffset)
    ayahCaretTop.translationX = caretOffsetX
    ayahCaretBottom.translationX = caretOffsetX

    lastMeasuredTopPillsBottom = topPillsRow.bottom
    lastMeasuredAudioCapsuleTop = audioCapsuleOuter.top

    val lp = ayahBarWrapper.layoutParams as LayoutParams
    if (lp.gravity != (Gravity.TOP or Gravity.LEFT) ||
      lp.leftMargin != targetLeft ||
      lp.topMargin != targetTop
    ) {
      lp.gravity = Gravity.TOP or Gravity.LEFT
      lp.leftMargin = targetLeft
      lp.topMargin = targetTop
      ayahBarWrapper.layoutParams = lp
    }
  }

  private fun updateProgressFillWidth() {
    val trackW = capsuleProgressTrack.width
    if (trackW <= 0) return
    val targetW = (trackW * syntheticProgressFraction.coerceIn(0f, 1f)).roundToInt()
    val lp = capsuleProgressFill.layoutParams
    if (lp.width != targetW) {
      lp.width = targetW
      capsuleProgressFill.layoutParams = lp
    }
  }

  fun applyPalette(newPalette: HybridViewPalette) {
    palette = newPalette
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    val opaqueFloatBg = newPalette.glassFloatBg or 0xFF000000.toInt()

    // Top floating monochrome pills (Z3 floatation, opaque surface so zero Mushaf calligraphy bleeds through)
    val topPillRadius = dpF(22f)
    navPill.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = opaqueFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = topPillRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      navPill,
      zLevel = 3,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = topPillRadius
    )
    toolsPill.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = opaqueFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = topPillRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      toolsPill,
      zLevel = 3,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = topPillRadius
    )

    backIcon.setTintColor(newPalette.textPrimary)
    backButton.background = HybridViewTokens.lightweightPressCircleDrawable(Color.TRANSPARENT, pressColor = newPalette.ripple)
    navDivider.setBackgroundColor(newPalette.divider)
    navPageDivider.setBackgroundColor(newPalette.divider)
    suraTitleText.setTextColor(newPalette.textPrimary)
    pageNumberText.setTextColor(newPalette.textSecondary)

    // Left action pill: Bookmark (Color 2) | Appearance (textSecondary)
    toolSaveOutlineIcon.setTintColor(newPalette.editorialAccent)
    toolSaveFilledIcon.setTintColor(newPalette.editorialAccent)
    toolSaveIconView.setImageDrawable(
      if (state.isCurrentPageBookmarked) toolSaveFilledIcon else toolSaveOutlineIcon
    )
    toolSaveText.setTextColor(
      if (state.isCurrentPageBookmarked) newPalette.editorialAccent else newPalette.textPrimary
    )
    toolDivider1.setBackgroundColor(newPalette.divider)
    toolAppearanceIcon.setTintColor(newPalette.textSecondary)
    toolAppearanceText.setTextColor(newPalette.textPrimary)

    // Bottom Audio Capsule (Z3 floatation + opaque fill + 1px specular rim + Z2 Color-1 CTA)
    val capsuleRadius = dpF(28f)
    audioCapsuleCard.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = opaqueFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = capsuleRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      audioCapsuleCard,
      zLevel = 3,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = capsuleRadius
    )
    capsulePlayIcon.setTintColor(newPalette.onCta)
    capsulePauseIcon.setTintColor(newPalette.onCta)
    capsulePlayBtn.background = HybridViewTokens.lightweightPressCircleDrawable(
      newPalette.ctaBackground,
      pressColor = newPalette.ripple
    )
    HybridViewTokens.applyZLayerFloatation(
      capsulePlayBtn,
      zLevel = 2,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.ctaBackground,
      radiusPx = dpF(19f)
    )
    capsuleQariTitle.setTextColor(newPalette.textPrimary)
    capsuleMetaText.setTextColor(newPalette.textSecondary)
    capsuleDownloadText.setTextColor(newPalette.textSecondary)
    capsuleDownloadAction.setTextColor(newPalette.accent)
    capsuleExpandIcon.setTintColor(newPalette.textSecondary)
    capsuleExpandBtn.background = HybridViewTokens.lightweightPressCircleDrawable(
      newPalette.surfaceVariant,
      newPalette.cardHairlineBorder,
      strokePx,
      newPalette.ripple
    )
    capsuleProgressTrack.background = HybridViewTokens.roundedDrawable(newPalette.surfaceVariant, radiusPx = pillRadius)
    capsuleProgressFill.background = HybridViewTokens.roundedDrawable(newPalette.accent, radiusPx = pillRadius)

    if (isAyahBarBuilt) applyAyahBarPalette(newPalette)
    if (isStudyDrawerBuilt) applyStudyDrawerPalette(newPalette)
    if (isRecitationTabBuilt) applyRecitationTabPalette(newPalette)
    if (isTafsirTabBuilt) applyTafsirTabPalette(newPalette)
    if (isTafsirPickerBuilt) applyTafsirPickerPalette(newPalette)
    if (isAppearanceSheetBuilt) applyAppearanceSheetPalette(newPalette)
  }

  private fun applyAyahBarPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val tagRadius = dpF(18f)
    val barRadius = dpF(22f)
    val opaqueFloatBg = newPalette.glassFloatBg or 0xFF000000.toInt()

    // Ayah reference tag pill: opaque surface + Z1 float, editorialAccent (Color 2) text
    ayahSelectionTagPill.setTextColor(newPalette.editorialAccent)
    ayahSelectionTagPill.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = opaqueFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = tagRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      ayahSelectionTagPill,
      zLevel = 1,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = tagRadius
    )

    // Floating Ayah Action Bar: opaque surface + Z3 floatation + seamless caret
    ayahActionPill.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = opaqueFloatBg,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = barRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      ayahActionPill,
      zLevel = 3,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = barRadius
    )
    ayahCaretTop.setColors(opaqueFloatBg, newPalette.floatHairlineBorder)
    ayahCaretBottom.setColors(opaqueFloatBg, newPalette.floatHairlineBorder)

    // Monochrome 1.65dp icons: Play (Color 1), Tafsir (textPrimary), Bookmark (Color 2), Copy & Share (textPrimary)
    ayahActionIcons.getOrNull(0)?.setTintColor(newPalette.accent)
    ayahActionIcons.getOrNull(1)?.setTintColor(newPalette.textPrimary)
    ayahBookmarkOutlineIcon?.setTintColor(newPalette.editorialAccent)
    ayahBookmarkFilledIcon?.setTintColor(newPalette.editorialAccent)
    ayahBookmarkImageView?.setImageDrawable(
      if (state.isSelectedAyahBookmarked) ayahBookmarkFilledIcon else ayahBookmarkOutlineIcon
    )
    ayahActionIcons.getOrNull(3)?.setTintColor(newPalette.textPrimary)
    ayahActionIcons.getOrNull(4)?.setTintColor(newPalette.textPrimary)
    ayahActionTexts.forEach { it.setTextColor(newPalette.textPrimary) }
    ayahBookmarkLabelView?.setTextColor(
      if (state.isSelectedAyahBookmarked) newPalette.editorialAccent else newPalette.textPrimary
    )
    ayahActionButtonBgs.forEach { it.setPressColor(newPalette.ripple) }
  }

  private fun applyStudyDrawerPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    val sheetRadius = dpF(STUDY_SHEET_CORNER_RADIUS_DP)

    // Visible static vertical fade scrim above the detached sheet top edge (0 FBO, 0 idle frames):
    // 0%..25% transparent -> smooth dim across visible Mushaf zone into 50%..100% sheet area.
    val scrimMid = if (newPalette.isDark) 0x36000000 else 0x1A000000
    val scrimBottom = if (newPalette.isDark) 0x52000000 else 0x26000000
    studyDrawerScrim.background = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, scrimMid, scrimBottom, scrimBottom)
    )

    // Detached Floating Apple-Style Study Sheet (Z4 Deep Float, 28dp all corners rounded, opaque gradient)
    studyDrawerSheet.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.cardSurface,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = sheetRadius,
      topGradientColor = newPalette.surfaceElevated
    )
    HybridViewTokens.applyZLayerFloatation(
      studyDrawerSheet,
      zLevel = 4,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = sheetRadius
    )

    drawerHandle.background = HybridViewTokens.roundedDrawable(
      ColorUtils.setAlphaComponent(newPalette.textSecondary, 90),
      radiusPx = pillRadius
    )
    drawerCollapseIcon.setTintColor(newPalette.textSecondary)

    // Recessed 3-tab track (dark well uses surface so surfaceElevated Z2 thumb rises visibly brighter)
    val railTrackColor = if (newPalette.isDark) newPalette.surface else newPalette.surfaceVariant
    drawerTabRail.background = HybridViewTokens.recessedTrackDrawable(
      trackColor = railTrackColor,
      innerShadowColor = ColorUtils.setAlphaComponent(
        newPalette.shadowTint,
        if (newPalette.isDark) 89 else 20
      ),
      borderColor = newPalette.cardHairlineBorder,
      radiusPx = pillRadius
    )
    val activeElevationPx = dpF(HybridViewTokens.zLevelElevationDp(2, newPalette.isDark))
    val thumbAmbientShadow = HybridViewTokens.zLevelAmbientShadowColor(2, newPalette.isDark, newPalette.shadowTint)
    val thumbSpotShadow = HybridViewTokens.zLevelSpotShadowColor(2, newPalette.isDark, newPalette.shadowTint)
    drawerTabButtons.forEach { (tab, tv) ->
      val selected = tab == state.selectedStudyTab
      val thumbDrawable = HybridViewTokens.floatingSurfaceDrawable(
        fillColor = newPalette.surfaceElevated,
        borderColor = newPalette.floatHairlineBorder,
        specularTopColor = newPalette.glassRimHighlight,
        strokeWidthPx = strokePx,
        radiusPx = pillRadius
      )
      thumbDrawable.alpha = if (selected) 255 else 0
      drawerTabThumbDrawables[tab] = thumbDrawable
      val pressDrawable = drawerTabPressDrawables.getValue(tab)
      pressDrawable.setPressColor(newPalette.ripple)
      tv.background = LayerDrawable(arrayOf<Drawable>(thumbDrawable, pressDrawable))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        tv.outlineAmbientShadowColor = thumbAmbientShadow
        tv.outlineSpotShadowColor = thumbSpotShadow
      }
      tv.elevation = if (selected) activeElevationPx else 0f
      tv.setTextColor(if (selected) newPalette.accent else newPalette.textSecondary)
    }
    lastRenderedDrawerPalette = null
  }

  private fun applyRecitationTabPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    recQariNameText.setTextColor(newPalette.textPrimary)
    recQariSubtitleText.setTextColor(newPalette.textSecondary)
    recQariSwitchBtn.setTextColor(newPalette.accent)
    recQariSwitchBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      newPalette.accentSoftBg,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    recDownloadRow.background = HybridViewTokens.roundedDrawable(
      fillColor = newPalette.surfaceVariant,
      strokeColor = newPalette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = dpF(12f)
    )
    recDownloadText.setTextColor(newPalette.textSecondary)
    recDownloadAction.setTextColor(newPalette.accent)
    recPrevIcon.setTintColor(newPalette.textPrimary)
    recNextIcon.setTintColor(newPalette.textPrimary)
    recStopIcon.setTintColor(newPalette.textPrimary)
    recPrevBtn.background = HybridViewTokens.lightweightPressCircleDrawable(Color.TRANSPARENT, pressColor = newPalette.ripple)
    recNextBtn.background = HybridViewTokens.lightweightPressCircleDrawable(Color.TRANSPARENT, pressColor = newPalette.ripple)
    recStopBtn.background = HybridViewTokens.lightweightPressCircleDrawable(Color.TRANSPARENT, pressColor = newPalette.ripple)
    recPlayIcon.setTintColor(newPalette.onCta)
    recPauseIcon.setTintColor(newPalette.onCta)
    recPlayBtn.background = HybridViewTokens.lightweightPressCircleDrawable(newPalette.ctaBackground, pressColor = newPalette.ripple)
    HybridViewTokens.applyZLayerFloatation(
      recPlayBtn,
      zLevel = 2,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.ctaBackground,
      radiusPx = dpF(24f)
    )
    recSpeedBtn.setTextColor(newPalette.textSecondary)
    recSpeedBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      newPalette.surfaceVariant,
      newPalette.cardHairlineBorder,
      strokePx,
      pillRadius,
      newPalette.ripple
    )
    recRepeatBtn.setTextColor(newPalette.textSecondary)
    recRepeatBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      newPalette.surfaceVariant,
      newPalette.cardHairlineBorder,
      strokePx,
      pillRadius,
      newPalette.ripple
    )
  }

  private fun applyTafsirTabPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    val cardRadius = dpF(16f)
    tafsirAyahTitle.setTextColor(newPalette.editorialAccent)
    tafsirListIcon.setTintColor(newPalette.accent)
    tafsirListText.setTextColor(newPalette.accent)
    tafsirListBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = newPalette.accentSoftBg,
      strokeColor = ColorUtils.setAlphaComponent(newPalette.accent, if (newPalette.isDark) 72 else 54),
      strokeWidthPx = strokePx,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    tafsirPrevIcon.setTintColor(newPalette.accent)
    tafsirNextIcon.setTintColor(newPalette.accent)
    tafsirBodyText.setTextColor(newPalette.textPrimary)

    // Z1 elevated inner card for Tafsir scrollable body
    tafsirBodyCard.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.surfaceElevated,
      borderColor = newPalette.cardHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = cardRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      tafsirBodyCard,
      zLevel = 1,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = cardRadius
    )
    tafsirOverflowFade.background = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(
        ColorUtils.setAlphaComponent(newPalette.surfaceElevated, 0),
        ColorUtils.setAlphaComponent(newPalette.surfaceElevated, 235),
        newPalette.surfaceElevated
      )
    )
    tafsirExpandIcon.setTintColor(newPalette.accent)
    tafsirExpandBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = newPalette.cardSurface,
      strokeColor = newPalette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    HybridViewTokens.applyZLayerFloatation(
      tafsirExpandBtn,
      zLevel = 1,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = pillRadius
    )

    tafsirEmptyCard.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.surfaceElevated,
      borderColor = newPalette.cardHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = cardRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      tafsirEmptyCard,
      zLevel = 1,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = cardRadius
    )
    tafsirEmptyTitle.setTextColor(newPalette.textPrimary)
    tafsirEmptyBody.setTextColor(newPalette.textSecondary)
    tafsirEmptyCta.setTextColor(newPalette.onCta)
    tafsirEmptyCta.background = HybridViewTokens.lightweightPressRoundedDrawable(
      newPalette.ctaBackground,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
  }

  private fun applyTafsirPickerPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    val sheetRadius = dpF(STUDY_SHEET_CORNER_RADIUS_DP)
    val scrimMid = if (newPalette.isDark) 0x46000000 else 0x22000000
    val scrimBottom = if (newPalette.isDark) 0x66000000 else 0x34000000
    tafsirPickerScrim.background = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(Color.TRANSPARENT, scrimMid, scrimBottom, scrimBottom)
    )
    tafsirPickerSheet.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.cardSurface,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = sheetRadius,
      topGradientColor = newPalette.surfaceElevated
    )
    HybridViewTokens.applyZLayerFloatation(
      tafsirPickerSheet,
      zLevel = 4,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = sheetRadius
    )
    tafsirPickerHandle.background = HybridViewTokens.roundedDrawable(
      ColorUtils.setAlphaComponent(newPalette.textSecondary, 90),
      radiusPx = pillRadius
    )
    tafsirPickerTitle.setTextColor(newPalette.textPrimary)
    tafsirPickerCloseText.setTextColor(newPalette.textPrimary)
    tafsirPickerCloseIcon.setTintColor(newPalette.textSecondary)
    tafsirPickerCloseBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = newPalette.surfaceVariant,
      strokeColor = newPalette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    tafsirPickerDownloadCta.setTextColor(newPalette.onCta)
    tafsirPickerDownloadCta.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = newPalette.ctaBackground,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    HybridViewTokens.applyZLayerFloatation(
      tafsirPickerDownloadCta,
      zLevel = 2,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.ctaBackground,
      radiusPx = pillRadius
    )
    if (tafsirPickerRows.isNotEmpty()) {
      renderTafsirPickerList(state.tafsirState)
    }
  }

  private fun updateOledToggleVisual(enabled: Boolean) {
    isOledToggleChecked = enabled
    val strokePx = dp(1f)
    val hairline = HybridViewTokens.luminousHairlineColor(palette, palette.divider)
    oledRowBg.setColor(palette.surfaceVariant)
    oledRowBg.setStroke(strokePx, hairline)
    oledTitleView.setTextColor(palette.textPrimary)
    oledSubtitleView.setTextColor(palette.textSecondary)
    oledTrackBg.setColor(if (enabled) palette.accent else palette.divider)
    oledThumbBg.setColor(if (enabled) palette.onAccent else Color.WHITE)
    val targetGravity = Gravity.CENTER_VERTICAL or if (enabled) Gravity.END else Gravity.START
    (oledSwitchThumb.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
      if (lp.gravity != targetGravity) {
        lp.gravity = targetGravity
        oledSwitchThumb.layoutParams = lp
      }
    }
  }

  private fun updateMatchAppToggleVisual(checked: Boolean) {
    isMatchAppChecked = checked
    val strokePx = dp(1.5f)
    if (checked) {
      matchAppBoxBg.setColor(palette.accent)
      matchAppBoxBg.setStroke(strokePx, palette.accent)
      matchAppCheckDrawable.setTintColor(palette.onAccent)
      matchAppCheckboxIcon.setImageDrawable(matchAppCheckDrawable)
    } else {
      matchAppBoxBg.setColor(Color.TRANSPARENT)
      matchAppBoxBg.setStroke(strokePx, palette.textSecondary)
      matchAppCheckboxIcon.setImageDrawable(null)
    }
  }

  private fun applyAppearanceSheetPalette(newPalette: HybridViewPalette) {
    val strokePx = dp(1f)
    val pillRadius = dpF(999f)
    val sheetRadius = dpF(STUDY_SHEET_CORNER_RADIUS_DP)
    val scrimMid = if (newPalette.isDark) 0x36000000 else 0x1A000000
    val scrimBottom = if (newPalette.isDark) 0x52000000 else 0x26000000
    appearanceSheetScrim.background = GradientDrawable(
      GradientDrawable.Orientation.TOP_BOTTOM,
      intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, scrimMid, scrimBottom, scrimBottom)
    )
    appearanceSheetCard.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.cardSurface,
      borderColor = newPalette.floatHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = sheetRadius,
      topGradientColor = newPalette.surfaceElevated
    )
    HybridViewTokens.applyZLayerFloatation(
      appearanceSheetCard,
      zLevel = 4,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = sheetRadius
    )
    appearanceSheetHandle.background = HybridViewTokens.roundedDrawable(
      ColorUtils.setAlphaComponent(newPalette.textSecondary, 90),
      radiusPx = pillRadius
    )
    appearanceSheetTitle.setTextColor(newPalette.textPrimary)
    appearanceSheetDoneText.setTextColor(newPalette.textPrimary)
    appearanceSheetCloseIcon.setTintColor(newPalette.textSecondary)
    appearanceSheetDoneBtn.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = newPalette.surfaceVariant,
      strokeColor = newPalette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = pillRadius,
      pressColor = newPalette.ripple
    )
    appearanceLabels.forEach { it.setTextColor(newPalette.textSecondary) }
    updateOledToggleVisual(isOledToggleChecked)
    matchAppLabel.setTextColor(newPalette.textPrimary)
    updateMatchAppToggleVisual(isMatchAppChecked)
    appStyleSwatchBgs.forEach { (style, swatchDrawable) ->
      val sp = HybridViewPalette.from(style, newPalette.isDark, newPalette.isOled)
      val selected = style == state.appearanceState.appThemeStyle
      val ringColor = if (selected) {
        ColorUtils.compositeColors(newPalette.accentSoftBg, newPalette.cardSurface)
      } else {
        newPalette.surfaceVariant
      }
      swatchDrawable.setColors(sp.accent, sp.editorialAccent, ringColor)
    }
    textBrightnessSeek.applyColors(newPalette.divider, newPalette.accent, newPalette.accent)
    bgBrightnessSeek.applyColors(newPalette.divider, newPalette.accent, newPalette.accent)
    lastAppearancePalette = null
  }

  override fun onDetachedFromWindow() {
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    cancelPostOpenTabPrebuilds()
    progressTickActive = false
    progressHandler.removeCallbacks(progressRunnable)
    HybridViewTokens.snapPendingVisibility(topPillsRow)
    HybridViewTokens.snapPendingVisibility(audioCapsuleOuter, hiddenVisibility = View.INVISIBLE)
    HybridViewTokens.resetApplePressFeedback(backButton)
    HybridViewTokens.resetApplePressFeedback(suraPageJumpArea)
    HybridViewTokens.resetApplePressFeedback(toolSaveBtn)
    HybridViewTokens.resetApplePressFeedback(toolAppearanceBtn)
    HybridViewTokens.resetApplePressFeedback(capsulePlayBtn)
    HybridViewTokens.resetApplePressFeedback(capsuleExpandBtn)
    HybridViewTokens.resetApplePressFeedback(audioCapsuleCard)
    if (isAyahBarBuilt) {
      HybridViewTokens.snapPendingVisibility(ayahBarWrapper, hiddenVisibility = View.INVISIBLE)
      ayahActionButtons.forEach { HybridViewTokens.resetApplePressFeedback(it) }
    }
    if (isStudyDrawerBuilt) {
      HybridViewTokens.snapPendingVisibility(studyDrawerScrim, hiddenVisibility = View.INVISIBLE)
      snapStudyDrawerPendingState()
      HybridViewTokens.resetApplePressFeedback(drawerCollapseBtn)
      drawerTabButtons.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
    }
    if (isRecitationTabBuilt) {
      HybridViewTokens.resetApplePressFeedback(recQariSwitchBtn)
      HybridViewTokens.resetApplePressFeedback(recDownloadAction)
      HybridViewTokens.resetApplePressFeedback(recPrevBtn)
      HybridViewTokens.resetApplePressFeedback(recPlayBtn)
      HybridViewTokens.resetApplePressFeedback(recNextBtn)
      HybridViewTokens.resetApplePressFeedback(recStopBtn)
      HybridViewTokens.resetApplePressFeedback(recSpeedBtn)
      HybridViewTokens.resetApplePressFeedback(recRepeatBtn)
    }
    if (isTafsirTabBuilt) {
      tafsirBodyScroll.removeCallbacks(tafsirOverflowCheckRunnable)
      tafsirExpandIconView.animate().cancel()
      HybridViewTokens.resetApplePressFeedback(tafsirListBtn)
      HybridViewTokens.resetApplePressFeedback(tafsirPrevBtn)
      HybridViewTokens.resetApplePressFeedback(tafsirNextBtn)
      HybridViewTokens.resetApplePressFeedback(tafsirExpandBtn)
      HybridViewTokens.resetApplePressFeedback(tafsirEmptyCta)
    }
    if (isTafsirPickerBuilt) {
      HybridViewTokens.snapPendingVisibility(tafsirPickerScrim, hiddenVisibility = View.INVISIBLE)
      snapTafsirPickerPendingState()
      HybridViewTokens.resetApplePressFeedback(tafsirPickerCloseBtn)
      HybridViewTokens.resetApplePressFeedback(tafsirPickerDownloadCta)
      tafsirPickerRows.forEach { HybridViewTokens.resetApplePressFeedback(it.row) }
    }
    if (isAppearanceSheetBuilt) {
      HybridViewTokens.snapPendingVisibility(appearanceSheetScrim, hiddenVisibility = View.INVISIBLE)
      snapAppearanceSheetPendingState()
      HybridViewTokens.resetApplePressFeedback(appearanceSheetDoneBtn)
      appModeButtons.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
      appStyleChips.values.forEach { (chip, _) -> HybridViewTokens.resetApplePressFeedback(chip) }
      pageColorButtons.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
      HybridViewTokens.resetApplePressFeedback(oledToggleRow)
      HybridViewTokens.resetApplePressFeedback(matchAppToggleRow)
    }
    super.onDetachedFromWindow()
  }

  fun unbind() {
    collectionJob?.cancel()
    collectionJob = null
    controller = null
    onBackToHub = null
    onJumpPage = null
    onTogglePageBookmark = null
    onAyahListen = null
    onAyahTafsir = null
    onAyahBookmark = null
    onAyahCopy = null
    onAyahShare = null
    onPlayPause = null
    onSkipNext = null
    onSkipPrev = null
    onStop = null
    onSpeedChanged = null
    onRepeatChanged = null
    onShowQariList = null
    onCancelDownload = null
    onAllowMobileDownload = null
    onOpenTafsirManager = null
    onTafsirStepAyah = null
    onAppThemeStyleSelected = null
    onAppThemeModeSelected = null
    onOledDarkModeChanged = null
    onPageAppearanceUpdated = null
    onMushafNudgeChanged = null
    studyDrawerHeightAnimator?.cancel()
    studyDrawerHeightAnimator = null
    if (isTafsirTabBuilt) {
      tafsirBodyScroll.removeCallbacks(tafsirOverflowCheckRunnable)
    }
    Looper.myQueue().removeIdleHandler(idlePrebuildHandler)
    cancelPostOpenTabPrebuilds()
    progressTickActive = false
    progressHandler.removeCallbacks(progressRunnable)
    removeAllViews()
  }

  internal fun hasStudyDrawerBuiltForTest(): Boolean = isStudyDrawerBuilt
  internal fun hasRecitationTabBuiltForTest(): Boolean = isRecitationTabBuilt
  internal fun hasTafsirTabBuiltForTest(): Boolean = isTafsirTabBuilt
  internal fun hasAppearanceTabBuiltForTest(): Boolean = isAppearanceSheetBuilt
  internal fun hasAppearanceSheetBuiltForTest(): Boolean = isAppearanceSheetBuilt
  internal fun isStudyDrawerRenderVisibleForTest(): Boolean = isStudyDrawerRenderVisible
  internal fun isAppearanceSheetRenderVisibleForTest(): Boolean = isAppearanceSheetRenderVisible
  internal fun navPillForTest(): View = navPill
  internal fun toolsPillForTest(): View = toolsPill
  internal fun toolAppearanceBtnForTest(): View = toolAppearanceBtn
  internal fun audioCapsuleCardForTest(): View = audioCapsuleCard
  internal fun capsulePlayBtnForTest(): View = capsulePlayBtn
  internal fun ayahBarWrapperForTest(): View? = if (isAyahBarBuilt) ayahBarWrapper else null
  internal fun ayahActionPillForTest(): View? = if (isAyahBarBuilt) ayahActionPill else null
  internal fun ayahSelectionTagPillForTest(): TextView? = if (isAyahBarBuilt) ayahSelectionTagPill else null
  internal fun studyDrawerScrimForTest(): View? = if (isStudyDrawerBuilt) studyDrawerScrim else null
  internal fun studyDrawerSheetForTest(): ViewGroup? = if (isStudyDrawerBuilt) studyDrawerSheet else null
  internal fun appearanceSheetScrimForTest(): View? = if (isAppearanceSheetBuilt) appearanceSheetScrim else null
  internal fun appearanceSheetCardForTest(): ViewGroup? = if (isAppearanceSheetBuilt) appearanceSheetCard else null
  internal fun appearanceSheetDoneBtnForTest(): View? = if (isAppearanceSheetBuilt) appearanceSheetDoneBtn else null
  internal fun oledToggleRowForTest(): View? = if (isAppearanceSheetBuilt) oledToggleRow else null
  internal fun isOledToggleCheckedForTest(): Boolean = isOledToggleChecked
  internal fun currentPaletteForTest(): HybridViewPalette = palette
  internal fun drawerTabButtonsForTest(): Map<HybridReaderContract.StudyTab, TextView> = drawerTabButtons
  internal fun recPlayBtnForTest(): View? = if (isRecitationTabBuilt) recPlayBtn else null
  internal fun recStopBtnForTest(): View? = if (isRecitationTabBuilt) recStopBtn else null
  internal fun recSpeedBtnForTest(): TextView? = if (isRecitationTabBuilt) recSpeedBtn else null
  internal fun recQariSubtitleForTest(): TextView? = if (isRecitationTabBuilt) recQariSubtitleText else null
  internal fun recDownloadRowForTest(): View? = if (isRecitationTabBuilt) recDownloadRow else null
  internal fun capsuleMetaForTest(): TextView = capsuleMetaText
  internal fun tafsirListBtnForTest(): View? = if (isTafsirTabBuilt) tafsirListBtn else null
  internal fun tafsirBodyCardForTest(): View? = if (isTafsirTabBuilt) tafsirBodyCard else null
  internal fun tafsirExpandBtnForTest(): View? = if (isTafsirTabBuilt) tafsirExpandBtn else null
  internal fun tafsirExpandIconViewForTest(): ImageView? = if (isTafsirTabBuilt) tafsirExpandIconView else null
  internal fun isTafsirExpandedForTest(): Boolean = isTafsirExpanded
  internal fun setTafsirOverflowForTest(overflows: Boolean) = updateTafsirOverflowControls(overflows)
  internal fun toggleTafsirExpandedForTest() = toggleTafsirExpanded()
  internal fun tafsirPickerSheetForTest(): ViewGroup? = if (isTafsirPickerBuilt) tafsirPickerSheet else null
  internal fun tafsirPickerDownloadCtaForTest(): View? = if (isTafsirPickerBuilt) tafsirPickerDownloadCta else null
  internal fun triggerIdlePrebuildForTest() {
    idlePrebuildHandler.queueIdle()
  }

  private companion object {
    private const val POST_OPEN_PREBUILD_START_DELAY_MS = 180L
    private const val STUDY_SHEET_HORIZONTAL_INSET_DP = 10f
    private const val STUDY_SHEET_BOTTOM_INSET_DP = 12f
    private const val STUDY_SHEET_CORNER_RADIUS_DP = 28f
  }

  internal class HybridBrightnessSliderView(context: Context) : View(context) {
    private val trackHeightPx = HybridViewTokens.dpF(context, 4f)
    private val trackRadiusPx = trackHeightPx * 0.5f
    private val thumbRadiusPx = HybridViewTokens.dpF(context, 8f)
    private val padHorizPx = thumbRadiusPx + HybridViewTokens.dpF(context, 4f)
    private val minHeightPx = HybridViewTokens.dp(context, 32f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val trackRect = RectF()
    private val activeRect = RectF()
    private var thumbCenterX = 0f
    private var centerY = 0f

    var max: Int = 100
      set(value) {
        val clampedMax = value.coerceAtLeast(1)
        if (field != clampedMax) {
          field = clampedMax
          if (progress > clampedMax) {
            progress = clampedMax
          } else {
            updateGeometry()
            invalidate()
          }
        }
      }

    var progress: Int = 0
      set(value) {
        val clamped = value.coerceIn(0, max)
        if (field != clamped) {
          field = clamped
          updateGeometry()
          invalidate()
        }
      }

    var onProgressChanged: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    init {
      isClickable = true
      isFocusable = true
      importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun hasOverlappingRendering(): Boolean = false

    fun applyColors(
      @ColorInt trackColor: Int,
      @ColorInt activeColor: Int,
      @ColorInt thumbColor: Int
    ) {
      var changed = false
      if (trackPaint.color != trackColor) {
        trackPaint.color = trackColor
        changed = true
      }
      if (activePaint.color != activeColor) {
        activePaint.color = activeColor
        changed = true
      }
      if (thumbPaint.color != thumbColor) {
        thumbPaint.color = thumbColor
        changed = true
      }
      if (changed) invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val desiredW = (padHorizPx * 2f + HybridViewTokens.dpF(context, 120f)).roundToInt()
      val w = resolveSize(desiredW, widthMeasureSpec)
      val h = resolveSize(minHeightPx, heightMeasureSpec)
      setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
      super.onSizeChanged(w, h, oldw, oldh)
      updateGeometry()
    }

    private fun updateGeometry() {
      val w = width.toFloat()
      val h = height.toFloat()
      if (w <= 0f || h <= 0f) return
      centerY = h * 0.5f
      val left = padHorizPx
      val right = (w - padHorizPx).coerceAtLeast(left)
      val top = centerY - trackHeightPx * 0.5f
      val bottom = centerY + trackHeightPx * 0.5f
      trackRect.set(left, top, right, bottom)
      val fraction = (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
      val span = right - left
      val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
      if (isRtl) {
        thumbCenterX = right - fraction * span
        activeRect.set(thumbCenterX, top, right, bottom)
      } else {
        thumbCenterX = left + fraction * span
        activeRect.set(left, top, thumbCenterX, bottom)
      }
    }

    override fun onDraw(canvas: Canvas) {
      if (trackRect.isEmpty) return
      canvas.drawRoundRect(trackRect, trackRadiusPx, trackRadiusPx, trackPaint)
      canvas.drawRoundRect(activeRect, trackRadiusPx, trackRadiusPx, activePaint)
      canvas.drawCircle(thumbCenterX, centerY, thumbRadiusPx, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
      if (!isEnabled) return false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          parent?.requestDisallowInterceptTouchEvent(true)
          updateProgressFromTouch(event.x)
          return true
        }
        MotionEvent.ACTION_MOVE -> {
          updateProgressFromTouch(event.x)
          return true
        }
        MotionEvent.ACTION_UP -> {
          parent?.requestDisallowInterceptTouchEvent(false)
          updateProgressFromTouch(event.x)
          performClick()
          return true
        }
        MotionEvent.ACTION_CANCEL -> {
          parent?.requestDisallowInterceptTouchEvent(false)
          return true
        }
      }
      return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
      return super.performClick()
    }

    private fun updateProgressFromTouch(touchX: Float) {
      val left = padHorizPx
      val right = (width - padHorizPx).toFloat().coerceAtLeast(left + 1f)
      val raw = ((touchX - left) / (right - left)).coerceIn(0f, 1f)
      val fraction = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - raw else raw
      val next = (fraction * max).roundToInt().coerceIn(0, max)
      if (next != progress) {
        progress = next
        onProgressChanged?.invoke(next, true)
      }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
      super.onInitializeAccessibilityNodeInfo(info)
      info.className = SeekBar::class.java.name
      info.isScrollable = true
      info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
        0f,
        max.toFloat(),
        progress.toFloat()
      )
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
      }
      if (progress < max) {
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
      }
      if (progress > 0) {
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
      }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
      if (super.performAccessibilityAction(action, arguments)) return true
      val step = (max / 20).coerceAtLeast(1)
      return when {
        action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
          val next = (progress + step).coerceIn(0, max)
          if (next != progress) {
            progress = next
            onProgressChanged?.invoke(next, true)
          }
          true
        }
        action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
          val next = (progress - step).coerceIn(0, max)
          if (next != progress) {
            progress = next
            onProgressChanged?.invoke(next, true)
          }
          true
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
          action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
          val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, -1f) ?: -1f
          if (value >= 0f) {
            val next = value.roundToInt().coerceIn(0, max)
            if (next != progress) {
              progress = next
              onProgressChanged?.invoke(next, true)
            }
            true
          } else {
            false
          }
        }
        else -> false
      }
    }
  }

  private class CaretView(
    context: Context,
    private val pointUp: Boolean
  ) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = HybridViewTokens.dpF(context, 1f)
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    private val fillPath = Path()
    private val strokePath = Path()

    fun setColors(@ColorInt fillColor: Int, @ColorInt strokeColor: Int) {
      fillPaint.color = fillColor
      strokePaint.color = strokeColor
      invalidate()
    }

    override fun onDraw(canvas: Canvas) {
      val w = width.toFloat()
      val h = height.toFloat()
      if (w <= 0f || h <= 0f) return
      fillPath.reset()
      strokePath.reset()
      if (pointUp) {
        fillPath.moveTo(w * 0.5f, 0f)
        fillPath.lineTo(w, h)
        fillPath.lineTo(0f, h)
        fillPath.close()
        strokePath.moveTo(0.5f, h)
        strokePath.lineTo(w * 0.5f, 0.5f)
        strokePath.lineTo(w - 0.5f, h)
      } else {
        fillPath.moveTo(0f, 0f)
        fillPath.lineTo(w, 0f)
        fillPath.lineTo(w * 0.5f, h)
        fillPath.close()
        strokePath.moveTo(0.5f, 0f)
        strokePath.lineTo(w * 0.5f, h - 0.5f)
        strokePath.lineTo(w - 0.5f, 0f)
      }
      canvas.drawPath(fillPath, fillPaint)
      if (Color.alpha(strokePaint.color) > 0) {
        canvas.drawPath(strokePath, strokePaint)
      }
    }
  }

  private class DuoToneSwatchDrawable(
    private val ringStrokePx: Float
  ) : Drawable() {
    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = ringStrokePx
    }
    private var alphaByte = 255

    fun setColors(@ColorInt primary: Int, @ColorInt secondary: Int, @ColorInt separatorRing: Int) {
      primaryPaint.color = primary
      secondaryPaint.color = secondary
      ringPaint.color = separatorRing
      invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
      val b = bounds
      if (b.isEmpty) return
      val w = b.width().toFloat()
      val h = b.height().toFloat()
      val r = ((h - ringStrokePx * 2f) * 0.5f).coerceAtLeast(1f)
      val cy = b.top + h * 0.5f
      val maxSpan = (w - ringStrokePx * 2f - r * 2f).coerceAtLeast(r * 0.7f)
      val offset = minOf(r * 1.35f, maxSpan)
      val midX = b.left + w * 0.5f
      val c1x = midX - offset * 0.5f
      val c2x = midX + offset * 0.5f

      // Dot 1: Color 1 (accent) + crisp ring
      canvas.drawCircle(c1x, cy, r, primaryPaint)
      if (Color.alpha(ringPaint.color) > 0) {
        canvas.drawCircle(c1x, cy, r, ringPaint)
      }

      // Dot 2: Color 2 (editorialAccent) overlapping Dot 1 with crisp separator ring
      canvas.drawCircle(c2x, cy, r, secondaryPaint)
      if (Color.alpha(ringPaint.color) > 0) {
        canvas.drawCircle(c2x, cy, r, ringPaint)
      }
    }

    override fun setAlpha(alpha: Int) {
      alphaByte = alpha.coerceIn(0, 255)
      primaryPaint.alpha = alphaByte
      secondaryPaint.alpha = alphaByte
      ringPaint.alpha = alphaByte
      invalidateSelf()
    }

    override fun getAlpha(): Int = alphaByte

    override fun setColorFilter(colorFilter: ColorFilter?) {
      primaryPaint.colorFilter = colorFilter
      secondaryPaint.colorFilter = colorFilter
      ringPaint.colorFilter = colorFilter
      invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
  }
}
