package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.quran.labs.androidquran.memorization.tasmee.VadWaveformMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight Canvas-based real-time VAD waveform visualizer `View` used inside
 * [KidsClassroomOverlayView] when docked in the Mushaf bottom recording area.
 *
 * Pulses its concentric VAD status indicator and 16 symmetrical capsule bars whenever
 * voice activity is detected, and stops frame invalidation immediately when hidden or detached.
 */
class VadWaveformView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  private val density = resources.displayMetrics.density

  private var isVadActive: Boolean = false
  private var micLevel: Float = 0f
  private var bars: FloatArray = FloatArray(VadWaveformMath.DEFAULT_BAR_COUNT)

  private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = Color.argb(145, 30, 41, 59)
  }

  private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }

  private val coreDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }

  private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }

  private val tempRect = RectF()

  fun updateWaveformState(
    vadActive: Boolean,
    rmsLevel: Float,
    waveformBars: List<Float>
  ) {
    val wasActive = this.isVadActive
    this.isVadActive = vadActive
    this.micLevel = ((rmsLevel - 0.008f).coerceAtLeast(0f) * 6.5f).coerceIn(0f, 1f)
    if (waveformBars.isNotEmpty()) {
      if (bars.size != waveformBars.size) {
        bars = FloatArray(waveformBars.size)
      }
      for (i in waveformBars.indices) {
        bars[i] = waveformBars[i].coerceIn(0f, 1f)
      }
    } else if (!vadActive) {
      bars.fill(0f)
    }
    if (wasActive != vadActive || isShown) {
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f) return

    // Rounded dark recording well background
    val wellCorner = dpF(10f)
    tempRect.set(0f, 0f, w, h)
    canvas.drawRoundRect(tempRect, wellCorner, wellCorner, wellPaint)

    val nowMs = SystemClock.uptimeMillis()
    val pulse = (0.5f + 0.5f * sin((nowMs % 900L).toDouble() / 900.0 * 2.0 * PI)).toFloat()

    val padH = dpF(10f)
    val centerY = h * 0.5f
    val haloRadiusMax = min(h * 0.38f, dpF(12f))
    val haloCenterX = padH + haloRadiusMax

    // 1. Concentric VAD Halo + Core Dot
    if (isVadActive) {
      val dynRadius = haloRadiusMax * (0.72f + 0.28f * pulse + 0.15f * micLevel).coerceAtMost(1.15f)
      haloPaint.color = Color.argb((75 + (65 * pulse).roundToInt()).coerceIn(40, 160), 16, 185, 129)
      canvas.drawCircle(haloCenterX, centerY, dynRadius, haloPaint)
      coreDotPaint.color = Color.rgb(16, 185, 129)
    } else {
      haloPaint.color = Color.argb(38, 148, 163, 184)
      canvas.drawCircle(haloCenterX, centerY, haloRadiusMax * 0.68f, haloPaint)
      coreDotPaint.color = Color.rgb(148, 163, 184)
    }
    val coreRadius = dpF(4.5f) * (if (isVadActive) (1.0f + 0.14f * pulse) else 1.0f)
    canvas.drawCircle(haloCenterX, centerY, coreRadius, coreDotPaint)

    // 2. Multi-bar VAD-reactive Waveform
    val barsLeft = haloCenterX + haloRadiusMax + dpF(10f)
    val barsRight = w - padH
    val barsWidth = (barsRight - barsLeft).coerceAtLeast(0f)
    val count = bars.size.coerceAtLeast(1)
    if (barsWidth <= dpF(16f)) return

    val slotW = barsWidth / count.toFloat()
    val barW = (slotW * 0.56f).coerceIn(dpF(2f), dpF(5f))
    val gap = ((barsWidth - barW * count) / (count - 1).coerceAtLeast(1)).coerceAtLeast(1f)
    val maxBarH = (h - dpF(8f)).coerceAtLeast(dpF(6f))
    val minBarH = (maxBarH * 0.18f).coerceAtLeast(dpF(3f))
    val barRadius = barW * 0.5f

    barPaint.color = if (isVadActive) {
      Color.rgb(16, 185, 129)
    } else {
      Color.argb(105, 148, 163, 184)
    }

    for (i in 0 until count) {
      val env = bars[i]
      val waveMod = if (isVadActive) {
        0.84f + 0.16f * sin((nowMs % 820L).toDouble() / 820.0 * 2.0 * PI + i * 0.5).toFloat()
      } else {
        1f
      }
      val centerBias = 1f - abs((i - count / 2f) / (count / 2f).coerceAtLeast(1f))
      val amp = if (isVadActive) {
        (max(env, micLevel * (0.5f + 0.5f * centerBias)) * waveMod).coerceIn(0.14f, 1f)
      } else {
        0.04f
      }
      val bh = minBarH + (maxBarH - minBarH) * amp
      val x = barsLeft + i * (barW + gap)
      tempRect.set(x, centerY - bh * 0.5f, x + barW, centerY + bh * 0.5f)
      canvas.drawRoundRect(tempRect, barRadius, barRadius, barPaint)
    }

    // Keep pulsing smoothly ONLY while VAD is actively detecting voice and the view is visible
    if (isVadActive && isAttachedToWindow && windowVisibility == VISIBLE && isShown) {
      postInvalidateOnAnimation()
    }
  }

  private fun dpF(value: Float): Float = value * density
}
