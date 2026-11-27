package com.quran.labs.androidquran.kidsclassroom

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val EASE_OUT_EXPO = PathInterpolator(0.23f, 1f, 0.32f, 1f)

/**
 * Attaches subtle, interruptible tactile press feedback (`scale = 0.94f`, 110 ms ease-out)
 * to any interactive control without consuming its click handling.
 */
@SuppressLint("ClickableViewAccessibility")
private fun View.attachTactilePressFeedback(pressedScale: Float = 0.94f) {
  setOnTouchListener { v, event ->
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        v.animate()
          .scaleX(pressedScale)
          .scaleY(pressedScale)
          .setDuration(110L)
          .setInterpolator(EASE_OUT_EXPO)
          .start()
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val target = (v.getTag(R.id.kids_classroom_overlay) as? Float) ?: 1f
        v.animate()
          .scaleX(target)
          .scaleY(target)
          .setDuration(160L)
          .setInterpolator(EASE_OUT_EXPO)
          .start()
      }
    }
    false
  }
}

/**
 * Minimal, distraction-free **Children Quran Play & Mic Bar** paired with flanking **Previous/Next
 * Ayah** quick navigators, a compact **Ayah & live-stage status pill**, and a subtle 1-click
 * **Options** reveal button below the Mushaf page (`PagerActivity`) so the Tajweed Mushaf remains
 * full-screen while word-by-word playback, hidden-word memorization, and grading highlights render
 * directly on the Mushaf glyphs.
 *
 * During an active learning or recording session (`QARI_RECITING`, `KIDS_REPEATING`,
 * `CHILD_RECITING`, `SCORING`), secondary chrome gently recedes (`alpha = 0.42f`, `scale = 0.94f`)
 * so the learner's attention stays calmly focused on the Quran glyphs and the active orb.
 */
class KidsClassroomOverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs), KidsClassroomManager.Listener {

  private val density = resources.displayMetrics.density
  private val playOrbView: KidsMinimalPlayOrbView
  private val prevAyahBtn: KidsAyahNavCircleView
  private val nextAyahBtn: KidsAyahNavCircleView
  private val ayahRepBadge: TextView
  private val ayahBadgeDrawable: GradientDrawable
  private val optionsRevealBtn: ImageButton

  private var lastFocusModeActive: Boolean? = null
  private var lastBadgeFillColor: Int = Color.argb(175, 15, 23, 42)
  private var lastBadgeStrokeColor: Int = Color.argb(95, 148, 163, 184)
  private var badgeColorAnimator: ValueAnimator? = null

  var onRequestOptionsSheet: (() -> Unit)? = null
  var onPreviousAyah: (() -> Unit)? = null
  var onNextAyah: (() -> Unit)? = null

  init {
    isClickable = false
    isFocusable = false
    clipToPadding = false
    clipChildren = false
    setPadding(0, dp(2), 0, dp(4))

    playOrbView = KidsMinimalPlayOrbView(context).apply {
      contentDescription = context.getString(R.string.kids_play_button_cd)
      tag = "kids_play_orb_btn"
      attachTactilePressFeedback(0.93f)
      setOnClickListener {
        handlePrimaryOrbTap()
      }
    }

    // RTL Quran direction: Left arrow (|<) proceeds to Next Ayah, Right arrow (>|) goes back to Previous Ayah
    nextAyahBtn = KidsAyahNavCircleView(context, pointLeft = true).apply {
      contentDescription = context.getString(R.string.kids_next_ayah_cd)
      tag = "kids_next_ayah_btn"
      attachTactilePressFeedback(0.91f)
      setOnClickListener {
        onNextAyah?.invoke() ?: KidsClassroomManager.advanceToNextAyah(context, autoPlay = false)
      }
    }

    prevAyahBtn = KidsAyahNavCircleView(context, pointLeft = false).apply {
      contentDescription = context.getString(R.string.kids_prev_ayah_cd)
      tag = "kids_prev_ayah_btn"
      attachTactilePressFeedback(0.91f)
      setOnClickListener {
        onPreviousAyah?.invoke() ?: KidsClassroomManager.goToPreviousAyah(context, autoPlay = false)
      }
    }

    val centerCluster = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = LAYOUT_DIRECTION_LTR
      clipChildren = false
      clipToPadding = false
      addView(
        nextAyahBtn,
        LinearLayout.LayoutParams(dp(48), dp(48)).apply {
          marginEnd = dp(4)
        }
      )
      addView(
        playOrbView,
        LinearLayout.LayoutParams(dp(58), dp(56)).apply {
          setMargins(0, dp(2), 0, dp(2))
        }
      )
      addView(
        prevAyahBtn,
        LinearLayout.LayoutParams(dp(48), dp(48)).apply {
          marginStart = dp(4)
        }
      )
    }

    ayahBadgeDrawable = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = dp(15).toFloat()
      setColor(lastBadgeFillColor)
      setStroke(dp(1), lastBadgeStrokeColor)
    }

    ayahRepBadge = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(typeface, Typeface.BOLD)
      fontFeatureSettings = "tnum"
      setTextColor(Color.WHITE)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
      gravity = Gravity.CENTER
      val hPad = dp(10)
      val vPad = dp(5)
      setPadding(hPad, vPad, hPad, vPad)
      background = ayahBadgeDrawable
      minHeight = dp(32)
      maxWidth = dp(126)
      alpha = 0.90f
      tag = "kids_ayah_repeat_badge"
      contentDescription = context.getString(R.string.memorization_options_menu)
      attachTactilePressFeedback(0.95f)
      setOnClickListener {
        onRequestOptionsSheet?.invoke()
      }
    }

    optionsRevealBtn = ImageButton(context).apply {
      setBackgroundResource(R.drawable.bg_subtle_options_pill)
      setImageResource(R.drawable.ic_recitation_options)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      val pad = dp(14)
      setPadding(pad, pad, pad, pad)
      alpha = 0.80f
      contentDescription = context.getString(R.string.memorization_options_menu)
      attachTactilePressFeedback(0.92f)
      setOnClickListener {
        onRequestOptionsSheet?.invoke()
      }
    }

    addView(
      ayahRepBadge,
      LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START).apply {
        marginStart = dp(10)
      }
    )

    addView(
      centerCluster,
      LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    )

    addView(
      optionsRevealBtn,
      LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.END).apply {
        marginEnd = dp(10)
      }
    )
  }

  private fun handlePrimaryOrbTap() {
    val cur = KidsClassroomManager.currentState()
    if (!cur.downloadProgress.isReady || cur.stage == ClassroomStage.DOWNLOADING_MODELS) {
      if (!cur.downloadProgress.isDownloading) {
        KidsClassroomManager.startModelDownload(context)
      }
      return
    }

    when {
      cur.stage == ClassroomStage.CHILD_RECITING -> {
        KidsClassroomManager.finishChildRecitationEarly()
      }
      cur.stage == ClassroomStage.QARI_RECITING ||
        cur.stage == ClassroomStage.KIDS_REPEATING ||
        cur.stage == ClassroomStage.TASMEE_STREAMING ||
        cur.stage == ClassroomStage.SCORING ||
        cur.isAutoTransitionPending -> {
        KidsClassroomManager.stopActiveLoop()
      }
      else -> {
        KidsClassroomManager.startThreeStageLoopForCurrentTurn(context)
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    KidsClassroomManager.addListener(this)
    renderState(KidsClassroomManager.currentState())
  }

  override fun onDetachedFromWindow() {
    KidsClassroomManager.removeListener(this)
    badgeColorAnimator?.cancel()
    badgeColorAnimator = null
    super.onDetachedFromWindow()
  }

  override fun onKidsClassroomStateChanged(state: KidsClassroomUiState) {
    post { renderState(state) }
  }

  private fun renderState(state: KidsClassroomUiState) {
    if (!state.isEnabled) {
      visibility = View.GONE
      return
    }
    visibility = View.VISIBLE
    playOrbView.bindState(state)

    val isSessionActive = state.stage == ClassroomStage.QARI_RECITING ||
      state.stage == ClassroomStage.KIDS_REPEATING ||
      state.stage == ClassroomStage.CHILD_RECITING ||
      state.stage == ClassroomStage.TASMEE_STREAMING ||
      state.stage == ClassroomStage.SCORING

    updateFocusChrome(isSessionActive)

    val isArabic = MemoryFlashcardManager.isAppInArabicMode(context)
    val sNum = MemoryFlashcardManager.formatNumber(state.currentAyah.sura, isArabic)
    val aNum = MemoryFlashcardManager.formatNumber(state.currentAyah.ayah, isArabic)
    val baseLabel = "$sNum:$aNum"

    val stageLabel: String? = when (state.stage) {
      ClassroomStage.QARI_RECITING ->
        if (isArabic) "● الشيخ" else "● Sheikh"
      ClassroomStage.KIDS_REPEATING ->
        if (isArabic) "● الأطفال" else "● Kids"
      ClassroomStage.CHILD_RECITING ->
        if (state.isVadActive) {
          if (isArabic) "● يستمع" else "● Listening"
        } else {
          if (isArabic) "● دورك" else "● Recite"
        }
      ClassroomStage.SCORING ->
        if (isArabic) "● تصحيح" else "● Checking"
      ClassroomStage.TURN_PASSED ->
        if (isArabic) "✓ أحسنت" else "✓ Passed"
      ClassroomStage.TURN_RETRY ->
        if (isArabic) "↻ أعد" else "↻ Retry"
      else -> null
    }

    ayahRepBadge.text = if (stageLabel != null) {
      "$stageLabel · $baseLabel"
    } else {
      when {
        state.ayahRepeatTarget > 1 -> {
          val cur = MemoryFlashcardManager.formatNumber(
            (state.ayahRepeatCompleted + 1).coerceAtMost(state.ayahRepeatTarget),
            isArabic
          )
          val tot = MemoryFlashcardManager.formatNumber(state.ayahRepeatTarget, isArabic)
          "$baseLabel · $cur/$tot"
        }
        state.surahRepeatTarget > 1 -> {
          val cur = MemoryFlashcardManager.formatNumber(
            (state.surahRepeatCompleted + 1).coerceAtMost(state.surahRepeatTarget),
            isArabic
          )
          val tot = MemoryFlashcardManager.formatNumber(state.surahRepeatTarget, isArabic)
          "$baseLabel · ↻$cur/$tot"
        }
        else -> baseLabel
      }
    }

    val (targetFill, targetStroke) = when {
      state.stage == ClassroomStage.CHILD_RECITING && state.isVadActive ->
        Color.argb(210, 5, 120, 87) to Color.argb(190, 52, 211, 153)
      state.stage == ClassroomStage.CHILD_RECITING ->
        Color.argb(200, 3, 105, 161) to Color.argb(180, 56, 189, 248)
      state.stage == ClassroomStage.QARI_RECITING ->
        Color.argb(200, 15, 118, 110) to Color.argb(175, 45, 212, 191)
      state.stage == ClassroomStage.KIDS_REPEATING ->
        Color.argb(200, 180, 83, 9) to Color.argb(175, 251, 191, 36)
      state.stage == ClassroomStage.SCORING ->
        Color.argb(200, 67, 56, 202) to Color.argb(175, 129, 140, 248)
      state.stage == ClassroomStage.TURN_PASSED ->
        Color.argb(210, 5, 120, 87) to Color.argb(190, 52, 211, 153)
      state.stage == ClassroomStage.TURN_RETRY ->
        Color.argb(200, 180, 83, 9) to Color.argb(180, 251, 191, 36)
      else ->
        Color.argb(175, 15, 23, 42) to Color.argb(95, 148, 163, 184)
    }
    animateBadgeColors(targetFill, targetStroke)
  }

  private fun updateFocusChrome(isSessionActive: Boolean) {
    if (lastFocusModeActive == isSessionActive) return
    lastFocusModeActive = isSessionActive

    val secondaryAlpha = if (isSessionActive) 0.42f else 0.92f
    val optionsAlpha = if (isSessionActive) 0.44f else 0.80f
    val secondaryScale = if (isSessionActive) 0.94f else 1.0f
    val durationMs = if (isSessionActive) 200L else 170L

    nextAyahBtn.setTag(R.id.kids_classroom_overlay, secondaryScale)
    prevAyahBtn.setTag(R.id.kids_classroom_overlay, secondaryScale)
    optionsRevealBtn.setTag(R.id.kids_classroom_overlay, secondaryScale)

    listOf(nextAyahBtn, prevAyahBtn).forEach { v ->
      v.animate()
        .alpha(secondaryAlpha)
        .scaleX(secondaryScale)
        .scaleY(secondaryScale)
        .setDuration(durationMs)
        .setInterpolator(EASE_OUT_EXPO)
        .start()
    }
    optionsRevealBtn.animate()
      .alpha(optionsAlpha)
      .scaleX(secondaryScale)
      .scaleY(secondaryScale)
      .setDuration(durationMs)
      .setInterpolator(EASE_OUT_EXPO)
      .start()
  }

  private fun animateBadgeColors(targetFill: Int, targetStroke: Int) {
    if (targetFill == lastBadgeFillColor && targetStroke == lastBadgeStrokeColor) return
    val startFill = lastBadgeFillColor
    val startStroke = lastBadgeStrokeColor
    badgeColorAnimator?.cancel()
    val evaluator = ArgbEvaluator()
    badgeColorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 190L
      interpolator = EASE_OUT_EXPO
      addUpdateListener { anim ->
        val f = anim.animatedValue as Float
        val cFill = evaluator.evaluate(f, startFill, targetFill) as Int
        val cStroke = evaluator.evaluate(f, startStroke, targetStroke) as Int
        lastBadgeFillColor = cFill
        lastBadgeStrokeColor = cStroke
        ayahBadgeDrawable.setColor(cFill)
        ayahBadgeDrawable.setStroke(dp(1), cStroke)
      }
      start()
    }
  }

  private fun dp(value: Int): Int = (value * density).roundToInt()
}

private class KidsAyahNavCircleView(
  context: Context,
  private val pointLeft: Boolean
) : View(context) {

  private val density = resources.displayMetrics.density
  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.argb(185, 15, 23, 42)
  }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1.2f * density
    color = Color.argb(120, 148, 163, 184)
  }
  private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = 2.2f * density
    color = Color.WHITE
  }
  private val chevronPath = Path()

  init {
    minimumWidth = (48 * density).roundToInt()
    minimumHeight = (48 * density).roundToInt()
    isClickable = true
    isFocusable = true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val cx = width * 0.5f
    val cy = height * 0.5f
    val radius = min(width, height) * 0.36f
    if (radius <= 2f) return

    canvas.drawCircle(cx, cy, radius, bgPaint)
    canvas.drawCircle(cx, cy, radius, borderPaint)

    val s = radius * 0.38f
    chevronPath.reset()
    if (pointLeft) {
      // Left-pointing arrow (|<) -> Proceed to Next Ayah in RTL Arabic
      canvas.drawLine(cx - s * 0.72f, cy - s * 0.80f, cx - s * 0.72f, cy + s * 0.80f, glyphPaint)
      chevronPath.moveTo(cx + s * 0.72f, cy - s * 0.85f)
      chevronPath.lineTo(cx - s * 0.28f, cy)
      chevronPath.lineTo(cx + s * 0.72f, cy + s * 0.85f)
      canvas.drawPath(chevronPath, glyphPaint)
    } else {
      // Right-pointing arrow (>|) -> Go Back to Previous Ayah in RTL Arabic
      chevronPath.moveTo(cx - s * 0.72f, cy - s * 0.85f)
      chevronPath.lineTo(cx + s * 0.28f, cy)
      chevronPath.lineTo(cx - s * 0.72f, cy + s * 0.85f)
      canvas.drawPath(chevronPath, glyphPaint)
      canvas.drawLine(cx + s * 0.72f, cy - s * 0.80f, cx + s * 0.72f, cy + s * 0.80f, glyphPaint)
    }
  }
}

private class KidsMinimalPlayOrbView(context: Context) : View(context) {

  private val density = resources.displayMetrics.density
  private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
  }
  private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.WHITE
  }
  private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = Color.WHITE
  }
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textAlign = Paint.Align.CENTER
    isFakeBoldText = true
    fontFeatureSettings = "tnum"
    textSize = 9.5f * resources.displayMetrics.scaledDensity
  }

  private val arcRect = RectF()
  private val iconPath = Path()

  private var currentStage: ClassroomStage = ClassroomStage.IDLE
  private var isVadActive: Boolean = false
  private var smoothVoiceEnergy: Float = 0f
  private var isDownloading: Boolean = false
  private var hasDownloadError: Boolean = false
  private var downloadPercent: Int = 0

  private var currentCoreColor: Int = Color.rgb(13, 148, 136)
  private var currentAuraColor: Int = Color.rgb(45, 212, 191)
  private var targetCoreColor: Int = currentCoreColor
  private var targetAuraColor: Int = currentAuraColor
  private var colorAnimator: ValueAnimator? = null

  private var pulsePhase: Float = 0f
  private var spinAngleDeg: Float = 0f
  private var activePulseAnimator: ValueAnimator? = null

  init {
    minimumWidth = (48 * density).roundToInt()
    minimumHeight = (48 * density).roundToInt()
    isClickable = true
    isFocusable = true
  }

  override fun onDetachedFromWindow() {
    colorAnimator?.cancel()
    colorAnimator = null
    activePulseAnimator?.cancel()
    activePulseAnimator = null
    super.onDetachedFromWindow()
  }

  fun bindState(state: KidsClassroomUiState) {
    currentStage = state.stage
    isVadActive = state.stage == ClassroomStage.CHILD_RECITING && state.isVadActive
    val barPeak = state.waveformBars.takeLast(4).maxOrNull() ?: 0f
    val rmsNorm = (state.micLevelRms * 4.2f).coerceIn(0f, 1f)
    val targetEnergy = if (state.stage == ClassroomStage.CHILD_RECITING) {
      max(barPeak, rmsNorm).coerceIn(if (isVadActive) 0.35f else 0.08f, 1f)
    } else {
      0f
    }
    // Smooth exponential filter so voice aura responds organically without jitter
    smoothVoiceEnergy = smoothVoiceEnergy + (targetEnergy - smoothVoiceEnergy) * 0.45f

    val dl = state.downloadProgress
    hasDownloadError = !dl.isReady && dl.errorMessage != null
    isDownloading = (!dl.isReady || state.stage == ClassroomStage.DOWNLOADING_MODELS) && !hasDownloadError
    downloadPercent = dl.progressPercent.coerceIn(0, 100)

    val (nextCore, nextAura) = resolveStageColors()
    animateColorsTo(nextCore, nextAura)
    syncPulseAnimator()
    invalidate()
  }

  private fun resolveStageColors(): Pair<Int, Int> = when {
    hasDownloadError -> Color.rgb(220, 38, 38) to Color.rgb(248, 113, 113)
    currentStage == ClassroomStage.CHILD_RECITING && isVadActive ->
      Color.rgb(5, 150, 105) to Color.rgb(52, 211, 153)
    currentStage == ClassroomStage.CHILD_RECITING ->
      Color.rgb(2, 132, 199) to Color.rgb(56, 189, 248)
    currentStage == ClassroomStage.QARI_RECITING ->
      Color.rgb(13, 148, 136) to Color.rgb(45, 212, 191)
    currentStage == ClassroomStage.KIDS_REPEATING ->
      Color.rgb(217, 119, 6) to Color.rgb(251, 191, 36)
    currentStage == ClassroomStage.SCORING ->
      Color.rgb(79, 70, 229) to Color.rgb(129, 140, 248)
    currentStage == ClassroomStage.TURN_PASSED ->
      Color.rgb(5, 150, 105) to Color.rgb(52, 211, 153)
    currentStage == ClassroomStage.TURN_RETRY ->
      Color.rgb(180, 83, 9) to Color.rgb(251, 191, 36)
    else -> Color.rgb(13, 148, 136) to Color.rgb(45, 212, 191)
  }

  private fun animateColorsTo(nextCore: Int, nextAura: Int) {
    if (nextCore == targetCoreColor && nextAura == targetAuraColor) return
    targetCoreColor = nextCore
    targetAuraColor = nextAura
    val startCore = currentCoreColor
    val startAura = currentAuraColor
    colorAnimator?.cancel()
    val evaluator = ArgbEvaluator()
    colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 190L
      interpolator = EASE_OUT_EXPO
      addUpdateListener { anim ->
        val f = anim.animatedValue as Float
        currentCoreColor = evaluator.evaluate(f, startCore, nextCore) as Int
        currentAuraColor = evaluator.evaluate(f, startAura, nextAura) as Int
        invalidate()
      }
      start()
    }
  }

  private fun syncPulseAnimator() {
    val shouldAnimate = currentStage == ClassroomStage.QARI_RECITING ||
      currentStage == ClassroomStage.KIDS_REPEATING ||
      currentStage == ClassroomStage.CHILD_RECITING ||
      currentStage == ClassroomStage.SCORING
    if (shouldAnimate) {
      if (activePulseAnimator?.isRunning != true) {
        activePulseAnimator?.cancel()
        activePulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 950L
          repeatCount = ValueAnimator.INFINITE
          interpolator = LinearInterpolator()
          addUpdateListener { anim ->
            val v = anim.animatedValue as Float
            pulsePhase = v
            spinAngleDeg = (v * 360f) % 360f
            invalidate()
          }
          start()
        }
      }
    } else {
      activePulseAnimator?.cancel()
      activePulseAnimator = null
      pulsePhase = 0f
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val cx = width * 0.5f
    val cy = height * 0.5f
    val minDim = min(width, height).toFloat()
    if (minDim <= 4f) return

    val coreRadius = minDim * 0.37f
    val breathWave = 0.5f + 0.5f * sin(pulsePhase * 2.0 * Math.PI).toFloat()

    val glowSpread = when (currentStage) {
      ClassroomStage.CHILD_RECITING -> minDim * (0.43f + 0.075f * smoothVoiceEnergy + 0.02f * breathWave)
      ClassroomStage.QARI_RECITING,
      ClassroomStage.KIDS_REPEATING -> minDim * (0.43f + 0.03f * breathWave)
      ClassroomStage.SCORING -> minDim * (0.44f + 0.025f * breathWave)
      else -> minDim * 0.43f
    }

    val auraAlpha = when {
      isVadActive -> 175
      currentStage == ClassroomStage.QARI_RECITING || currentStage == ClassroomStage.CHILD_RECITING -> 125
      else -> 88
    }

    glowPaint.shader = RadialGradient(
      cx,
      cy,
      max(glowSpread, coreRadius + 4f),
      intArrayOf(
        Color.argb(auraAlpha, Color.red(currentAuraColor), Color.green(currentAuraColor), Color.blue(currentAuraColor)),
        Color.argb(0, Color.red(currentAuraColor), Color.green(currentAuraColor), Color.blue(currentAuraColor))
      ),
      floatArrayOf(0.45f, 1f),
      Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy, glowSpread, glowPaint)

    corePaint.color = currentCoreColor
    canvas.drawCircle(cx, cy, coreRadius, corePaint)

    ringPaint.strokeWidth = (if (isVadActive) 2.0f else 1.5f) * density
    ringPaint.color = Color.argb(195, Color.red(currentAuraColor), Color.green(currentAuraColor), Color.blue(currentAuraColor))
    canvas.drawCircle(cx, cy, coreRadius + 1.2f * density, ringPaint)

    if (isDownloading) {
      val r = coreRadius - 3.5f * density
      arcRect.set(cx - r, cy - r, cx + r, cy + r)
      ringPaint.strokeWidth = 2.2f * density
      ringPaint.color = Color.argb(70, 255, 255, 255)
      canvas.drawOval(arcRect, ringPaint)
      ringPaint.color = Color.WHITE
      val sweep = max(18f, 360f * (downloadPercent / 100f))
      canvas.drawArc(arcRect, -90f, sweep, false, ringPaint)
      val textY = cy - (textPaint.descent() + textPaint.ascent()) * 0.5f
      canvas.drawText("$downloadPercent%", cx, textY, textPaint)
      return
    }

    when (currentStage) {
      ClassroomStage.CHILD_RECITING -> drawMicGlyph(canvas, cx, cy, coreRadius * 0.48f)
      ClassroomStage.SCORING -> drawScoringSpinner(canvas, cx, cy, coreRadius * 0.52f)
      ClassroomStage.TURN_PASSED -> drawCheckGlyph(canvas, cx, cy, coreRadius * 0.44f)
      ClassroomStage.QARI_RECITING,
      ClassroomStage.KIDS_REPEATING,
      ClassroomStage.TASMEE_STREAMING -> drawStopGlyph(canvas, cx, cy, coreRadius * 0.34f)
      else -> drawPlayGlyph(canvas, cx, cy, coreRadius * 0.40f)
    }
  }

  private fun drawScoringSpinner(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
    arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
    ringPaint.strokeWidth = 2.4f * density
    ringPaint.color = Color.argb(70, 255, 255, 255)
    canvas.drawOval(arcRect, ringPaint)
    ringPaint.color = Color.WHITE
    canvas.drawArc(arcRect, spinAngleDeg - 90f, 115f, false, ringPaint)
  }

  private fun drawCheckGlyph(canvas: Canvas, cx: Float, cy: Float, s: Float) {
    iconStrokePaint.strokeWidth = max(2.2f * density, s * 0.22f)
    iconPath.reset()
    iconPath.moveTo(cx - s * 0.72f, cy + s * 0.05f)
    iconPath.lineTo(cx - s * 0.16f, cy + s * 0.60f)
    iconPath.lineTo(cx + s * 0.76f, cy - s * 0.55f)
    canvas.drawPath(iconPath, iconStrokePaint)
  }

  private fun drawPlayGlyph(canvas: Canvas, cx: Float, cy: Float, halfSize: Float) {
    iconPath.reset()
    val shiftX = halfSize * 0.16f
    iconPath.moveTo(cx - halfSize * 0.72f + shiftX, cy - halfSize * 0.88f)
    iconPath.lineTo(cx + halfSize * 0.92f + shiftX, cy)
    iconPath.lineTo(cx - halfSize * 0.72f + shiftX, cy + halfSize * 0.88f)
    iconPath.close()
    canvas.drawPath(iconPath, iconPaint)
  }

  private fun drawStopGlyph(canvas: Canvas, cx: Float, cy: Float, halfSize: Float) {
    val r = 2.4f * density
    arcRect.set(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize)
    canvas.drawRoundRect(arcRect, r, r, iconPaint)
  }

  private fun drawMicGlyph(canvas: Canvas, cx: Float, cy: Float, s: Float) {
    val capW = s * 0.42f
    val capTop = cy - s * 0.88f
    val capBottom = cy + s * 0.18f
    arcRect.set(cx - capW, capTop, cx + capW, capBottom)
    canvas.drawRoundRect(arcRect, capW, capW, iconPaint)

    iconStrokePaint.strokeWidth = max(1.8f * density, s * 0.18f)
    val cradleR = s * 0.72f
    arcRect.set(cx - cradleR, cy - cradleR * 0.78f, cx + cradleR, cy + cradleR * 0.62f)
    canvas.drawArc(arcRect, 0f, 180f, false, iconStrokePaint)

    val stemTop = cy + cradleR * 0.62f
    val stemBottom = cy + s * 0.92f
    canvas.drawLine(cx, stemTop, cx, stemBottom, iconStrokePaint)
    canvas.drawLine(cx - s * 0.40f, stemBottom, cx + s * 0.40f, stemBottom, iconStrokePaint)
  }
}
