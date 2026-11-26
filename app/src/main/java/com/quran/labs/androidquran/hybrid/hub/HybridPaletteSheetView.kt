package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable

/**
 * Lightweight detached Z4 Native Android View sheet overlay for Display Mode & Duo-Tone
 * Palette selection on the Main Hub. Keeps plain color names ONLY and renders an overlapping
 * 2-dot duo-tone swatch (Color 1 + Color 2) with crisp separator ring per theme.
 */
class HybridPaletteSheetView(
  context: Context,
  private val onSelectThemeMode: (String) -> Unit,
  private val onSelectThemeStyle: (AppThemeStyle) -> Unit,
  private val onDismiss: () -> Unit
) : FrameLayout(context) {

  /**
   * Anti-aliased overlapping 2-dot duo-tone swatch (Color 1 accent + Color 2 editorialAccent
   * separated by a crisp ring matching the chip background).
   */
  internal class DuoToneSwatchView(context: Context) : View(context) {
    private val dotRadiusPx = HybridViewTokens.dpF(context, 7.5f)
    private val ringStrokePx = HybridViewTokens.dpF(context, 1.75f)
    private val overlapPx = HybridViewTokens.dpF(context, 4.5f)
    private val totalWidthPx = ((dotRadiusPx * 4f - overlapPx + ringStrokePx * 2f) + 0.5f).toInt()
    private val totalHeightPx = ((dotRadiusPx * 2f + ringStrokePx * 2f) + 0.5f).toInt()

    @ColorInt var primaryColor: Int = 0xFF23394F.toInt()
      private set
    @ColorInt var editorialColor: Int = 0xFF9B6D22.toInt()
      private set
    @ColorInt var ringColor: Int = Color.WHITE
      private set

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val editorialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = ringStrokePx
    }

    fun setColors(
      @ColorInt primary: Int,
      @ColorInt editorial: Int,
      @ColorInt separatorRing: Int
    ) {
      if (primaryColor != primary || editorialColor != editorial || ringColor != separatorRing) {
        primaryColor = primary
        editorialColor = editorial
        ringColor = separatorRing
        primaryPaint.color = primary
        editorialPaint.color = editorial
        ringPaint.color = separatorRing
        invalidate()
      }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      setMeasuredDimension(
        resolveSize(totalWidthPx, widthMeasureSpec),
        resolveSize(totalHeightPx, heightMeasureSpec)
      )
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val cy = height * 0.5f
      val c1x = ringStrokePx + dotRadiusPx
      val c2x = c1x + (dotRadiusPx * 2f - overlapPx)

      // Dot 1: Primary structure color (C1)
      canvas.drawCircle(c1x, cy, dotRadiusPx, primaryPaint)
      canvas.drawCircle(c1x, cy, dotRadiusPx, ringPaint)

      // Dot 2: Editorial/sacred accent color (C2) overlapping C1 with crisp separator ring
      canvas.drawCircle(c2x, cy, dotRadiusPx, editorialPaint)
      canvas.drawCircle(c2x, cy, dotRadiusPx, ringPaint)
    }
  }

  private data class StyleChipHolder(
    val chip: LinearLayout,
    val swatch: DuoToneSwatchView,
    val label: TextView,
    val checkView: ImageView,
    val checkDrawable: VectorPathDrawable
  )

  private val sheetCard = LinearLayout(context)
  private val sheetRadiusPx = HybridViewTokens.dpF(context, 28f)
  private val sheetHorizontalMarginPx = HybridViewTokens.dp(context, 10f)
  private val sheetBottomMarginBasePx = HybridViewTokens.dp(context, 12f)
  private val dragHandle = View(context)
  private val titleView = TextView(context)
  private val doneButton = LinearLayout(context)
  private val doneTextView = TextView(context)
  private val closeIconDrawable = HybridArchitecturalIcons.drawable(
    HybridArchitecturalIcons.CLOSE,
    Color.GRAY,
    HybridViewTokens.dp(context, 14f)
  )
  private val modeLabelView = TextView(context)
  private val modeRow = LinearLayout(context)
  private val colorLabelView = TextView(context)
  private val modeButtons = LinkedHashMap<String, TextView>()
  private val styleChips = LinkedHashMap<AppThemeStyle, StyleChipHolder>()

  private var activeMode: String = Constants.THEME_DEFAULT
  private var activeStyle: AppThemeStyle = AppThemeStyle.BEIGE
  private var currentPalette = HybridViewPalette.from(AppThemeStyle.BEIGE, false)

  internal fun sheetCardForTest(): View = sheetCard
  internal fun doneButtonForTest(): View = doneButton
  internal fun modeButtonForTest(mode: String): TextView = modeButtons.getValue(mode)
  internal fun styleChipForTest(style: AppThemeStyle): View = styleChips.getValue(style).chip
  internal fun styleSwatchForTest(style: AppThemeStyle): DuoToneSwatchView =
    styleChips.getValue(style).swatch
  internal fun styleLabelForTest(style: AppThemeStyle): TextView =
    styleChips.getValue(style).label
  internal fun styleCheckViewForTest(style: AppThemeStyle): ImageView =
    styleChips.getValue(style).checkView

  init {
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipChildren = false
    clipToPadding = false
    isClickable = true
    isFocusable = true
    setOnClickListener { onDismiss() }

    sheetCard.apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      isClickable = true
      isFocusable = true
      val hPad = HybridViewTokens.dp(context, 18f)
      val tPad = HybridViewTokens.dp(context, 14f)
      val bPad = HybridViewTokens.dp(context, 20f)
      setPadding(hPad, tPad, hPad, bPad)
      HybridViewTokens.applyZLayerFloatation(
        view = this,
        zLevel = 4,
        isDark = false,
        shadowTintColor = 0xFF18212C.toInt(),
        radiusPx = sheetRadiusPx
      )
    }

    // Drag handle
    dragHandle.background = HybridViewTokens.roundedDrawable(0x44888888, radiusPx = 999f)
    sheetCard.addView(
      dragHandle,
      LinearLayout.LayoutParams(
        HybridViewTokens.dp(context, 38f),
        HybridViewTokens.dp(context, 4f)
      ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = HybridViewTokens.dp(context, 12f)
      }
    )

    // Header: Appearance + Done pill with monochrome CLOSE icon
    val headerRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    titleView.apply {
      text = context.getString(R.string.hybrid_hub_palette_sheet_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    doneTextView.apply {
      text = context.getString(R.string.hybrid_hub_done)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    doneButton.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      contentDescription = context.getString(R.string.hybrid_hub_done)
      val ph = HybridViewTokens.dp(context, 12f)
      val pv = HybridViewTokens.dp(context, 6f)
      setPadding(ph, pv, ph, pv)
      addView(doneTextView)
      addView(
        ImageView(context).apply { setImageDrawable(closeIconDrawable) },
        LinearLayout.LayoutParams(
          HybridViewTokens.dp(context, 14f),
          HybridViewTokens.dp(context, 14f)
        ).apply { marginStart = HybridViewTokens.dp(context, 5f) }
      )
      setOnClickListener { onDismiss() }
      HybridViewTokens.attachApplePressFeedback(this, 0.965f)
    }
    headerRow.addView(
      titleView,
      LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    )
    headerRow.addView(doneButton)
    sheetCard.addView(headerRow)

    // Display Mode
    modeLabelView.apply {
      text = context.getString(R.string.hybrid_hub_theme_mode_label)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    sheetCard.addView(
      modeLabelView,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = HybridViewTokens.dp(context, 14f)
        bottomMargin = HybridViewTokens.dp(context, 8f)
      }
    )

    modeRow.apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val p = HybridViewTokens.dp(context, 4f)
      setPadding(p, p, p, p)
    }
    val modes = listOf(
      Constants.THEME_DEFAULT to R.string.hybrid_reader_mode_auto,
      Constants.THEME_LIGHT to R.string.hybrid_reader_mode_light,
      Constants.THEME_DARK to R.string.hybrid_reader_mode_dark
    )
    modes.forEachIndexed { index, (mode, labelRes) ->
      val btn = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = HybridViewTokens.dp(context, 36f)
        setOnClickListener { onSelectThemeMode(mode) }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      modeButtons[mode] = btn
      modeRow.addView(
        btn,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (index < modes.lastIndex) marginEnd = HybridViewTokens.dp(context, 4f)
        }
      )
    }
    sheetCard.addView(modeRow)

    // Duo-Tone Calm Palette (Plain color names ONLY)
    colorLabelView.apply {
      text = context.getString(R.string.hybrid_hub_theme_color_label)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    sheetCard.addView(
      colorLabelView,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = HybridViewTokens.dp(context, 16f)
        bottomMargin = HybridViewTokens.dp(context, 8f)
      }
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
    val checkPx = HybridViewTokens.dp(context, 12f)
    styles.forEachIndexed { index, (style, labelRes) ->
      val chip = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        val pv = HybridViewTokens.dp(context, 8f)
        val ph = HybridViewTokens.dp(context, 4f)
        setPadding(ph, pv, ph, pv)
        setOnClickListener { onSelectThemeStyle(style) }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      val swatch = DuoToneSwatchView(context)
      val label = TextView(context).apply {
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        gravity = Gravity.CENTER
        maxLines = 1
      }
      val checkDrawable = HybridArchitecturalIcons.drawable(
        HybridArchitecturalIcons.CHECK,
        Color.GRAY,
        checkPx
      )
      val checkView = ImageView(context).apply {
        setImageDrawable(checkDrawable)
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.INVISIBLE
      }
      chip.addView(
        swatch,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
      chip.addView(
        label,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 5f) }
      )
      chip.addView(
        checkView,
        LinearLayout.LayoutParams(checkPx, checkPx).apply {
          topMargin = HybridViewTokens.dp(context, 2f)
        }
      )
      styleChips[style] = StyleChipHolder(
        chip = chip,
        swatch = swatch,
        label = label,
        checkView = checkView,
        checkDrawable = checkDrawable
      )
      styleRow.addView(
        chip,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (index < styles.lastIndex) marginEnd = HybridViewTokens.dp(context, 6f)
        }
      )
    }
    sheetCard.addView(styleRow)

    addView(
      sheetCard,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
        leftMargin = sheetHorizontalMarginPx
        rightMargin = sheetHorizontalMarginPx
        bottomMargin = sheetBottomMarginBasePx
      }
    )
  }

  fun update(
    palette: HybridViewPalette,
    themeMode: String,
    themeStyle: AppThemeStyle,
    navBottomInsetPx: Int
  ) {
    currentPalette = palette
    activeMode = themeMode
    activeStyle = themeStyle

    setBackgroundColor(palette.scrim)
    val strokePx = HybridViewTokens.dp(context, 1f)
    sheetCard.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = palette.cardSurface,
      borderColor = palette.floatHairlineBorder,
      specularTopColor = palette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = sheetRadiusPx,
      topGradientColor = palette.surfaceElevated
    )
    HybridViewTokens.applyZLayerFloatation(
      view = sheetCard,
      zLevel = 4,
      isDark = palette.isDark,
      shadowTintColor = palette.shadowTint,
      radiusPx = sheetRadiusPx
    )
    (sheetCard.layoutParams as? LayoutParams)?.let { lp ->
      val targetBottom = sheetBottomMarginBasePx + navBottomInsetPx
      if (lp.leftMargin != sheetHorizontalMarginPx ||
        lp.rightMargin != sheetHorizontalMarginPx ||
        lp.bottomMargin != targetBottom
      ) {
        lp.leftMargin = sheetHorizontalMarginPx
        lp.rightMargin = sheetHorizontalMarginPx
        lp.bottomMargin = targetBottom
        sheetCard.layoutParams = lp
      }
    }

    dragHandle.background = HybridViewTokens.roundedDrawable(
      ColorUtils.setAlphaComponent(palette.textSecondary, 90),
      radiusPx = 999f
    )
    titleView.setTextColor(palette.textPrimary)
    doneTextView.setTextColor(palette.textPrimary)
    closeIconDrawable.setTintColor(palette.textSecondary)
    doneButton.background = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = palette.surfaceVariant,
      strokeColor = palette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = HybridViewTokens.dpF(context, 999f),
      pressColor = palette.ripple
    )
    modeLabelView.setTextColor(palette.textSecondary)
    colorLabelView.setTextColor(palette.textSecondary)

    val pillRadius = HybridViewTokens.dpF(context, 999f)
    val trackInnerShadow = ColorUtils.setAlphaComponent(
      palette.shadowTint,
      if (palette.isDark) 90 else 22
    )
    modeRow.background = HybridViewTokens.recessedTrackDrawable(
      trackColor = if (palette.isDark) palette.surface else palette.surfaceVariant,
      innerShadowColor = trackInnerShadow,
      borderColor = palette.cardHairlineBorder,
      radiusPx = pillRadius
    )
    modeButtons.forEach { (mode, btn) ->
      val selected = mode == activeMode
      btn.isSelected = selected
      btn.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
      btn.setTextColor(if (selected) palette.onCta else palette.textSecondary)
      if (selected) {
        btn.background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = palette.ctaBackground,
          radiusPx = pillRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = btn,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.ctaBackground,
          radiusPx = pillRadius
        )
      } else {
        btn.background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = Color.TRANSPARENT,
          radiusPx = pillRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = btn,
          zLevel = 0,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = pillRadius
        )
      }
    }

    val chipRadius = HybridViewTokens.dpF(context, 14f)
    styleChips.forEach { (style, holder) ->
      val selected = style == activeStyle
      holder.chip.isSelected = selected
      holder.label.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
      holder.label.setTextColor(if (selected) palette.accent else palette.textPrimary)
      holder.checkDrawable.setTintColor(palette.accent)
      holder.checkView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
      if (selected) {
        holder.chip.background = HybridViewTokens.floatingSurfaceDrawable(
          fillColor = palette.accentSoftBg,
          borderColor = palette.accent,
          specularTopColor = palette.glassRimHighlight,
          strokeWidthPx = HybridViewTokens.dp(context, 1.5f),
          radiusPx = chipRadius
        )
        HybridViewTokens.applyZLayerFloatation(
          view = holder.chip,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = chipRadius
        )
      } else {
        holder.chip.background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = palette.surfaceVariant,
          strokeColor = palette.cardHairlineBorder,
          strokeWidthPx = strokePx,
          radiusPx = chipRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = holder.chip,
          zLevel = 0,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = chipRadius
        )
      }
      val stylePalette = HybridViewPalette.from(style, palette.isDark)
      val ringColor = if (selected) {
        ColorUtils.compositeColors(palette.accentSoftBg, palette.cardSurface)
      } else {
        palette.surfaceVariant
      }
      holder.swatch.setColors(
        primary = stylePalette.accent,
        editorial = stylePalette.editorialAccent,
        separatorRing = ringColor
      )
    }
  }

  fun setSheetVisible(visible: Boolean) {
    val prevTarget = getTag(R.id.hybrid_anim_target_visible) as? Boolean
    val isCurrentlyVisible = visibility == View.VISIBLE
    if (prevTarget == visible && isCurrentlyVisible == visible) return
    setTag(R.id.hybrid_anim_target_visible, visible)

    val canAnimate = isAttachedToWindow &&
      isLaidOut &&
      AdaptiveMotionTokens.areAnimationsEnabled(context)
    val slidePx = HybridViewTokens.dpF(context, 36f)

    animate().cancel()
    sheetCard.animate().cancel()
    if (visible) {
      val wasHidden = !isCurrentlyVisible
      visibility = View.VISIBLE
      if (canAnimate && wasHidden) {
        alpha = 0f
        sheetCard.translationY = slidePx
        animate()
          .alpha(1f)
          .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .start()
        sheetCard.animate()
          .translationY(0f)
          .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .start()
      } else {
        alpha = 1f
        sheetCard.translationY = 0f
      }
    } else {
      if (canAnimate && isCurrentlyVisible) {
        animate()
          .alpha(0f)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .withEndAction {
            if (getTag(R.id.hybrid_anim_target_visible) == false) {
              visibility = View.GONE
              alpha = 1f
              sheetCard.translationY = 0f
            }
          }
          .start()
        sheetCard.animate()
          .translationY(slidePx)
          .setDuration(AdaptiveMotionTokens.QUICK_FADE_DURATION_MS.toLong())
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .start()
      } else {
        visibility = View.GONE
        alpha = 1f
        sheetCard.translationY = 0f
      }
    }
  }

  override fun onDetachedFromWindow() {
    HybridViewTokens.snapPendingVisibility(this)
    sheetCard.animate().cancel()
    sheetCard.translationY = 0f
    HybridViewTokens.resetApplePressFeedback(doneButton)
    modeButtons.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
    styleChips.values.forEach { HybridViewTokens.resetApplePressFeedback(it.chip) }
    super.onDetachedFromWindow()
  }
}
