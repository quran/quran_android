package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.util.QuranUtils

/**
 * Native [AlertDialog] for setting the Daily Wird page goal without Compose Dialog overhead.
 */
object DailyWirdGoalDialogNative {

  private val PRESETS = listOf(4, 10, 20, 30, 40)

  fun show(
    context: Context,
    initialGoalPages: Int,
    palette: HybridViewPalette,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
  ): AlertDialog {
    var goal = initialGoalPages.coerceIn(1, 604)

    val cardRadiusPx = HybridViewTokens.dpF(context, 24f)
    val card = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val p = HybridViewTokens.dp(context, 20f)
      setPadding(p, p, p, HybridViewTokens.dp(context, 16f))
      background = HybridViewTokens.floatingSurfaceDrawable(
        fillColor = palette.cardSurface,
        borderColor = palette.floatHairlineBorder,
        specularTopColor = palette.glassRimHighlight,
        strokeWidthPx = HybridViewTokens.dp(context, 1f),
        radiusPx = cardRadiusPx,
        topGradientColor = if (palette.isDark) palette.surfaceElevated else palette.glassFloatBg
      )
      HybridViewTokens.applyZLayerFloatation(
        view = this,
        zLevel = 4,
        isDark = palette.isDark,
        shadowTintColor = palette.shadowTint,
        radiusPx = cardRadiusPx
      )
    }

    val titleView = TextView(context).apply {
      text = context.getString(R.string.hybrid_hub_wird_goal_title)
      setTextColor(palette.textPrimary)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitleView = TextView(context).apply {
      text = context.getString(R.string.hybrid_hub_wird_goal_subtitle)
      setTextColor(palette.textSecondary)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    val goalText = TextView(context).apply {
      setTextColor(palette.editorialAccent)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      gravity = Gravity.CENTER
    }

    val presetButtons = LinkedHashMap<Int, TextView>()

    fun refreshUi() {
      goalText.text = context.getString(
        R.string.hybrid_hub_wird_goal_unit,
        QuranUtils.getLocalizedNumber(goal)
      )
      val radius = HybridViewTokens.dpF(context, 999f)
      val stroke = HybridViewTokens.dp(context, 1f)
      presetButtons.forEach { (preset, btn) ->
        val selected = preset == goal
        btn.isSelected = selected
        btn.setTextColor(if (selected) palette.onCta else palette.textPrimary)
        btn.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
        btn.background = if (selected) {
          HybridViewTokens.interactiveRoundedDrawable(palette.ctaBackground, radiusPx = radius, rippleColor = palette.ripple)
        } else {
          HybridViewTokens.interactiveRoundedDrawable(palette.surfaceVariant, palette.divider, stroke, radius, palette.ripple)
        }
      }
    }

    fun stepperButton(symbol: String, onClick: () -> Unit): TextView {
      val size = HybridViewTokens.dp(context, 38f)
      return TextView(context).apply {
        text = symbol
        setTextColor(palette.textPrimary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        width = size
        height = size
        background = HybridViewTokens.interactiveCircleDrawable(
          palette.surfaceVariant,
          palette.divider,
          HybridViewTokens.dp(context, 1f),
          palette.ripple
        )
        setOnClickListener { onClick() }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
      }
    }

    val minusBtn = stepperButton("-") {
      if (goal > 1) {
        goal -= 1
        refreshUi()
      }
    }
    val plusBtn = stepperButton("+") {
      if (goal < 604) {
        goal += 1
        refreshUi()
      }
    }

    val stepperRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      addView(minusBtn)
      addView(
        goalText,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      )
      addView(plusBtn)
    }

    val presetsRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
    PRESETS.forEachIndexed { idx, preset ->
      val chip = TextView(context).apply {
        text = QuranUtils.getLocalizedNumber(preset)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = HybridViewTokens.dp(context, 34f)
        setOnClickListener {
          goal = preset
          refreshUi()
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      presetButtons[preset] = chip
      presetsRow.addView(
        chip,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          if (idx < PRESETS.lastIndex) marginEnd = HybridViewTokens.dp(context, 6f)
        }
      )
    }

    lateinit var dialog: AlertDialog

    val actionsRow = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.END
      layoutDirection = View.LAYOUT_DIRECTION_RTL
    }
    val cancelBtn = Button(context).apply {
      text = context.getString(R.string.hybrid_hub_cancel)
      isAllCaps = false
      setTextColor(palette.textSecondary)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      background = HybridViewTokens.interactiveRoundedDrawable(
        android.graphics.Color.TRANSPARENT,
        radiusPx = HybridViewTokens.dpF(context, 999f),
        rippleColor = palette.ripple
      )
      setOnClickListener { dialog.dismiss() }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    val saveBtn = Button(context).apply {
      text = context.getString(R.string.hybrid_hub_save)
      isAllCaps = false
      setTextColor(palette.onCta)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      val ph = HybridViewTokens.dp(context, 18f)
      val pv = HybridViewTokens.dp(context, 8f)
      setPadding(ph, pv, ph, pv)
      background = HybridViewTokens.interactiveRoundedDrawable(
        palette.ctaBackground,
        radiusPx = HybridViewTokens.dpF(context, 999f),
        rippleColor = palette.ripple
      )
      setOnClickListener {
        onSave(goal)
        dialog.dismiss()
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
    }
    actionsRow.addView(cancelBtn)
    actionsRow.addView(
      saveBtn,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { marginStart = HybridViewTokens.dp(context, 8f) }
    )

    card.addView(titleView)
    card.addView(
      subtitleView,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = HybridViewTokens.dp(context, 4f) }
    )
    card.addView(
      stepperRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = HybridViewTokens.dp(context, 16f) }
    )
    card.addView(
      presetsRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = HybridViewTokens.dp(context, 14f) }
    )
    card.addView(
      actionsRow,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = HybridViewTokens.dp(context, 18f) }
    )

    refreshUi()

    val outerPad = HybridViewTokens.dp(context, 16f)
    val dialogRoot = FrameLayout(context).apply {
      clipChildren = false
      clipToPadding = false
      setBackgroundColor(Color.TRANSPARENT)
      setPadding(outerPad, outerPad, outerPad, outerPad)
      addView(
        card,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
    }

    dialog = AlertDialog.Builder(context)
      .setView(dialogRoot)
      .setOnDismissListener { onDismiss() }
      .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
    return dialog
  }
}
