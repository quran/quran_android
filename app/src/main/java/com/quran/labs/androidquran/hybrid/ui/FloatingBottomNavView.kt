package com.quran.labs.androidquran.hybrid.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab

/**
 * Native View Z3 floating pill bottom navigation bar replacing Compose `FloatingBottomNav`
 * on Main Hub and Search. Preserves exact RTL ordering (`القراءة | الفهرس | بحث | المحفوظات`),
 * monochrome `1.65dp` architectural stroke icons, 1dp vertical hairline dividers,
 * `isSelected`, and `contentDescription` for E2E parity.
 */
class FloatingBottomNavView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private data class TabHolder(
    val tab: HybridNavTab,
    val container: LinearLayout,
    val iconView: ImageView,
    val iconDrawable: VectorPathDrawable,
    val labelView: TextView,
    val bgDrawable: HybridViewTokens.LightweightPressColorDrawable
  )

  private val pillContainer = LinearLayout(context)
  private val pillRadiusPx = HybridViewTokens.dpF(context, 999f)
  private val dividers = mutableListOf<View>()
  private val tabHolders = mutableListOf<TabHolder>()

  private var selectedTab: HybridNavTab = HybridNavTab.INDEX
  private var onTabSelectedListener: ((HybridNavTab) -> Unit)? = null
  private var currentPalette: HybridViewPalette? = null
  private var lastRipplePalette: HybridViewPalette? = null
  private var staticPillLayerEnabled: Boolean = false

  init {
    id = R.id.hybrid_bottom_nav
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipChildren = false
    clipToPadding = false

    val hMargin = HybridViewTokens.dp(context, 14f)
    val bMargin = HybridViewTokens.dp(context, 12f)
    val tPad = HybridViewTokens.dp(context, 2f)
    setPadding(hMargin, tPad, hMargin, bMargin)

    pillContainer.apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val padH = HybridViewTokens.dp(context, 6f)
      val padV = HybridViewTokens.dp(context, 6f)
      setPadding(padH, padV, padH, padV)
      HybridViewTokens.applyZLayerFloatation(
        view = this,
        zLevel = 3,
        isDark = false,
        shadowTintColor = 0xFF18212C.toInt(),
        radiusPx = pillRadiusPx
      )
    }

    val tabs = HybridNavTab.entries
    val iconPx = HybridViewTokens.dp(context, 18f)
    val dividerWidth = HybridViewTokens.dp(context, 1f)
    val dividerHeight = HybridViewTokens.dp(context, 18f)
    val itemPadH = HybridViewTokens.dp(context, 4f)
    val itemPadV = HybridViewTokens.dp(context, 6f)

    tabs.forEachIndexed { index, tab ->
      val (iconSpec, labelResId) = when (tab) {
        HybridNavTab.READ -> HybridArchitecturalIcons.BOOK to R.string.hybrid_nav_read
        HybridNavTab.INDEX -> HybridArchitecturalIcons.INDEX to R.string.hybrid_nav_index
        HybridNavTab.SEARCH -> HybridArchitecturalIcons.SEARCH to R.string.hybrid_nav_search
        HybridNavTab.BOOKMARKS -> HybridArchitecturalIcons.BOOKMARK to R.string.hybrid_nav_bookmarks
      }
      val drawable = HybridArchitecturalIcons.drawable(iconSpec, Color.GRAY, iconPx)
      val iconView = ImageView(context).apply {
        setImageDrawable(drawable)
        scaleType = ImageView.ScaleType.FIT_CENTER
      }
      val label = context.getString(labelResId)
      val labelView = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        typeface = Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        includeFontPadding = false
      }

      val bgDrawable = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = pillRadiusPx,
        pressColor = Color.LTGRAY
      )

      val itemLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(itemPadH, itemPadV, itemPadH, itemPadV)
        isClickable = true
        isFocusable = true
        contentDescription = label
        background = bgDrawable
        addView(iconView, LinearLayout.LayoutParams(iconPx, iconPx))
        addView(
          labelView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply {
            topMargin = HybridViewTokens.dp(context, 2f)
          }
        )
        setOnClickListener {
          onTabSelectedListener?.invoke(tab)
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }

      val itemParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      ).apply {
        val gap = HybridViewTokens.dp(context, 2f)
        marginStart = gap
        marginEnd = gap
      }
      pillContainer.addView(itemLayout, itemParams)

      tabHolders.add(
        TabHolder(
          tab = tab,
          container = itemLayout,
          iconView = iconView,
          iconDrawable = drawable,
          labelView = labelView,
          bgDrawable = bgDrawable
        )
      )

      if (index < tabs.lastIndex) {
        val divider = View(context)
        val divParams = LinearLayout.LayoutParams(dividerWidth, dividerHeight)
        pillContainer.addView(divider, divParams)
        dividers.add(divider)
      }
    }

    addView(
      pillContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    )
  }

  fun setSelectedTab(tab: HybridNavTab) {
    val tabChanged = selectedTab != tab
    if (tabChanged || currentPalette == null) {
      selectedTab = tab
      refreshSelectionAndColors(animateSelection = tabChanged)
    }
  }

  fun setOnTabSelectedListener(listener: ((HybridNavTab) -> Unit)?) {
    onTabSelectedListener = listener
  }

  fun setStaticPillLayerEnabled(enabled: Boolean) {
    staticPillLayerEnabled = enabled
    val targetLayer = if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
    if (pillContainer.layerType != targetLayer) {
      pillContainer.setLayerType(targetLayer, null)
    }
  }

  fun unbind() {
    onTabSelectedListener = null
    tabHolders.forEach { HybridViewTokens.resetApplePressFeedback(it.container) }
    if (staticPillLayerEnabled && pillContainer.layerType != View.LAYER_TYPE_NONE) {
      pillContainer.setLayerType(View.LAYER_TYPE_NONE, null)
    }
  }

  fun applyPalette(palette: HybridViewPalette) {
    currentPalette = palette
    val strokePx = HybridViewTokens.dp(context, 1f)
    pillContainer.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = palette.glassFloatBg or 0xFF000000.toInt(),
      borderColor = palette.floatHairlineBorder,
      specularTopColor = palette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = pillRadiusPx
    )
    HybridViewTokens.applyZLayerFloatation(
      view = pillContainer,
      zLevel = 3,
      isDark = palette.isDark,
      shadowTintColor = palette.shadowTint,
      radiusPx = pillRadiusPx
    )
    dividers.forEach { it.setBackgroundColor(palette.divider) }
    refreshSelectionAndColors(animateSelection = false)
  }

  private fun refreshSelectionAndColors(animateSelection: Boolean = false) {
    val palette = currentPalette ?: return
    val paletteChanged = lastRipplePalette != palette
    if (paletteChanged) {
      lastRipplePalette = palette
    }
    val canAnimate = animateSelection &&
      isAttachedToWindow &&
      isLaidOut &&
      AdaptiveMotionTokens.areAnimationsEnabled(context)
    tabHolders.forEach { holder ->
      val isSelected = holder.tab == selectedTab
      val selectionChanged = holder.container.isSelected != isSelected
      if (selectionChanged || paletteChanged) {
        holder.container.isSelected = isSelected
        val tint = if (isSelected) palette.accent else palette.textSecondary
        holder.iconDrawable.setTintColor(tint)
        holder.labelView.setTextColor(tint)
        holder.bgDrawable.setFillColor(if (isSelected) palette.accentSoftBg else Color.TRANSPARENT)
        if (paletteChanged) {
          holder.bgDrawable.setPressColor(palette.ripple)
        }
      }
      if (isSelected && canAnimate && holder.container.scaleX >= 0.999f) {
        holder.container.animate().cancel()
        holder.container.scaleX = AdaptiveMotionTokens.PRESS_SCALE
        holder.container.scaleY = AdaptiveMotionTokens.PRESS_SCALE
        holder.container.animate()
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(AdaptiveMotionTokens.SPRING_SETTLE_DURATION_MS)
          .setInterpolator(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED)
          .start()
      }
    }
  }

  override fun onDetachedFromWindow() {
    tabHolders.forEach { HybridViewTokens.resetApplePressFeedback(it.container) }
    super.onDetachedFromWindow()
  }
}
