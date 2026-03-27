package com.quran.labs.androidquran.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R

/**
 * A custom view that draws a mic icon with concentric ripple circles
 * radiating outward during voice search recording.
 *
 * The view measures at the standard icon size (24dp) so it doesn't
 * disrupt toolbar layout. Ripple rings draw beyond the view bounds
 * into the toolbar's natural padding — [clipChildren] is disabled
 * on the parent when the animation starts.
 */
class PulsingMicView(
  context: Context,
  @ColorInt private val rippleColor: Int
) : View(context) {

  private val density = resources.displayMetrics.density
  private val touchTargetPx = (48 * density).toInt()
  private val iconSizePx = (24 * density)

  private val micDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mic)?.mutate()?.apply {
    setTint(rippleColor)
  }

  private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1.5f * density
    color = rippleColor
  }

  private val ringCount = 3
  private val ringPhases = FloatArray(ringCount) // 0f..1f progress for each ring
  private val staggerDelay = 700L
  private val ringDuration = 2100L
  private val maxRippleRadiusPx = 20 * density // how far rings expand beyond the icon edge

  private val animators = mutableListOf<ValueAnimator>()

  private val savedClipStates = mutableListOf<Pair<ViewGroup, Boolean>>()

  fun startAnimation() {
    stopAnimation()
    // Disable clipping on ancestors up to the toolbar so ripples
    // aren't hidden behind sibling views (e.g. the search box)
    var current: ViewGroup? = parent as? ViewGroup
    var depth = 0
    while (current != null && depth < 3) {
      savedClipStates.add(current to current.clipChildren)
      current.clipChildren = false
      current = current.parent as? ViewGroup
      depth++
    }
    for (i in 0 until ringCount) {
      val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ringDuration
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        startDelay = i * staggerDelay
        addUpdateListener { anim ->
          ringPhases[i] = anim.animatedValue as Float
          // Invalidate a larger area so the parent redraws our overflow
          val extra = (iconSizePx / 2f + maxRippleRadiusPx + ripplePaint.strokeWidth).toInt()
          (parent as? View)?.invalidate(
            left - extra, top - extra, right + extra, bottom + extra
          )
          invalidate()
        }
        start()
      }
      animators.add(animator)
    }
  }

  fun stopAnimation() {
    animators.forEach { it.cancel() }
    animators.clear()
    for (i in ringPhases.indices) ringPhases[i] = 0f
    for ((viewGroup, wasClipping) in savedClipStates) {
      viewGroup.clipChildren = wasClipping
    }
    savedClipStates.clear()
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    // 48dp touch target — the icon draws centered within this area
    setMeasuredDimension(
      resolveSize(touchTargetPx, widthMeasureSpec),
      resolveSize(touchTargetPx, heightMeasureSpec)
    )
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val cx = width / 2f
    val cy = height / 2f
    val iconRadius = iconSizePx / 2f

    // Draw ripple rings (these extend beyond view bounds)
    for (i in 0 until ringCount) {
      val phase = ringPhases[i]
      if (phase > 0f) {
        val radius = iconRadius + maxRippleRadiusPx * phase
        val alpha = ((1f - phase) * 160).toInt().coerceIn(0, 255)
        ripplePaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, ripplePaint)
      }
    }

    // Draw mic icon centered
    micDrawable?.let { drawable ->
      val iconHalf = (iconSizePx / 2f).toInt()
      drawable.setBounds(
        (cx - iconHalf).toInt(),
        (cy - iconHalf).toInt(),
        (cx + iconHalf).toInt(),
        (cy + iconHalf).toInt()
      )
      drawable.draw(canvas)
    }
  }

  override fun onDetachedFromWindow() {
    stopAnimation()
    super.onDetachedFromWindow()
  }
}
