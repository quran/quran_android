package com.quran.labs.androidquran.memorization.tasmee

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Signal-processing helpers for computing smooth 16-bar real-time voice envelopes
 * and Voice Activity Detection (VAD) status with short speech hangover so syllable
 * boundaries pulse naturally without chattering.
 */
object VadWaveformMath {
  const val DEFAULT_BAR_COUNT = 16
  const val LIVE_TASMEE_VAD_RMS_THRESHOLD = 0.016f
  const val VAD_HANGOVER_MS = 280L

  // Center-weighted contour so speech energy forms an organic vocal waveform silhouette
  private val ENVELOPE_WEIGHTS = floatArrayOf(
    0.46f, 0.58f, 0.72f, 0.84f, 0.94f, 1.00f, 0.92f, 1.00f,
    0.96f, 1.00f, 0.90f, 0.98f, 0.82f, 0.70f, 0.56f, 0.44f
  )

  fun emptyBars(count: Int = DEFAULT_BAR_COUNT): List<Float> = List(count) { 0f }

  fun updateBarsFromFloatChunk(
    history: FloatArray,
    samples: FloatArray,
    sampleCount: Int,
    isVadActive: Boolean
  ): List<Float> {
    if (history.isEmpty()) return emptyList()
    val barCount = history.size
    if (sampleCount <= 0) {
      for (i in 0 until barCount) {
        history[i] *= 0.78f
      }
      return history.toList()
    }

    val half = max(1, sampleCount / 2)
    var sumLeft = 0.0
    for (i in 0 until half) {
      val v = samples[i].toDouble()
      sumLeft += v * v
    }
    var sumRight = 0.0
    val rightCount = max(1, sampleCount - half)
    for (i in half until sampleCount) {
      val v = samples[i].toDouble()
      sumRight += v * v
    }

    val rmsLeft = sqrt(sumLeft / half.toDouble()).toFloat()
    val rmsRight = sqrt(sumRight / rightCount.toDouble()).toFloat()
    return pushSubChunkRms(history, rmsLeft, rmsRight, isVadActive)
  }

  fun updateBarsFromShortChunk(
    history: FloatArray,
    samples: ShortArray,
    sampleCount: Int,
    isVadActive: Boolean
  ): List<Float> {
    if (history.isEmpty()) return emptyList()
    val barCount = history.size
    if (sampleCount <= 0) {
      for (i in 0 until barCount) {
        history[i] *= 0.78f
      }
      return history.toList()
    }

    val inv = 1.0 / 32768.0
    val half = max(1, sampleCount / 2)
    var sumLeft = 0.0
    for (i in 0 until half) {
      val v = samples[i].toDouble() * inv
      sumLeft += v * v
    }
    var sumRight = 0.0
    val rightCount = max(1, sampleCount - half)
    for (i in half until sampleCount) {
      val v = samples[i].toDouble() * inv
      sumRight += v * v
    }

    val rmsLeft = sqrt(sumLeft / half.toDouble()).toFloat()
    val rmsRight = sqrt(sumRight / rightCount.toDouble()).toFloat()
    return pushSubChunkRms(history, rmsLeft, rmsRight, isVadActive)
  }

  private fun pushSubChunkRms(
    history: FloatArray,
    rmsLeft: Float,
    rmsRight: Float,
    isVadActive: Boolean
  ): List<Float> {
    val barCount = history.size
    val gain = if (isVadActive) 10.5f else 4.2f
    val leftNorm = ((rmsLeft - 0.004f).coerceAtLeast(0f) * gain).coerceIn(0f, 1f)
    val rightNorm = ((rmsRight - 0.004f).coerceAtLeast(0f) * gain).coerceIn(0f, 1f)

    if (barCount >= 4) {
      for (i in 0 until barCount - 2) {
        history[i] = history[i + 2] * 0.90f
      }
      history[barCount - 2] = max(leftNorm, history[barCount - 2] * 0.62f)
      history[barCount - 1] = max(rightNorm, history[barCount - 1] * 0.62f)
    } else {
      for (i in 0 until barCount) {
        history[i] = rightNorm
      }
    }

    return List(barCount) { idx ->
      val weight = ENVELOPE_WEIGHTS[idx % ENVELOPE_WEIGHTS.size]
      val mirrorIdx = barCount - 1 - idx
      val blended = 0.65f * history[idx] + 0.35f * history[mirrorIdx]
      val floor = if (isVadActive) 0.16f else 0.05f
      (blended * weight).coerceIn(floor, 1f)
    }
  }
}

/**
 * Real-time Voice Activity Detection (VAD) waveform visualizer for the bottom recording area.
 *
 * Pulses dynamically based on [isVadActive], [micLevel], and [waveformBars] while the user is
 * reciting so they can immediately see when their voice is being detected and recorded.
 */
@Composable
fun VadWaveformVisualizer(
  isVadActive: Boolean,
  micLevel: Float,
  waveformBars: List<Float>,
  modifier: Modifier = Modifier,
  accentColor: Color = Color(0xFF10B981),
  listeningColor: Color = Color(0xFF0D9488),
  inactiveColor: Color = Color(0xFF94A3B8),
  compact: Boolean = false
) {
  val infiniteTransition = rememberInfiniteTransition(label = "vadPulseTransition")
  val pulsePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "vadPulsePhase"
  )

  val vadStrength by animateFloatAsState(
    targetValue = if (isVadActive) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "vadStrength"
  )

  val smoothedMic by animateFloatAsState(
    targetValue = micLevel.coerceIn(0f, 1f),
    animationSpec = tween(durationMillis = 90),
    label = "smoothedMic"
  )

  val activeTint by animateColorAsState(
    targetValue = if (isVadActive) accentColor else listeningColor,
    animationSpec = tween(durationMillis = 180),
    label = "vadTint"
  )

  val vadAccessibilityDesc = if (isVadActive) {
    "Voice activity detected, recording recitation"
  } else {
    "Microphone listening, waiting for voice"
  }

  val containerHeight = if (compact) 22.dp else 38.dp
  val haloContainerSize = if (compact) 20.dp else 32.dp
  val coreDotSize = if (compact) 8.dp else 11.dp

  Row(
    modifier = modifier
      .height(containerHeight)
      .semantics { contentDescription = vadAccessibilityDesc }
      .testTag("vad_waveform_visualizer"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
  ) {
    // Concentric VAD status pulse halo
    Box(
      modifier = Modifier
        .size(haloContainerSize)
        .testTag("vad_status_indicator"),
      contentAlignment = Alignment.Center
    ) {
      val outerScale = if (isVadActive) {
        1.0f + 0.42f * pulsePhase + 0.25f * smoothedMic
      } else {
        0.82f + 0.10f * pulsePhase
      }
      val outerAlpha = if (isVadActive) {
        (0.24f + 0.28f * (1f - abs(pulsePhase - 0.5f) * 2f)).coerceIn(0.15f, 0.55f)
      } else {
        0.14f
      }

      Box(
        modifier = Modifier
          .size(haloContainerSize * 0.82f)
          .graphicsLayer {
            scaleX = outerScale
            scaleY = outerScale
            alpha = outerAlpha
          }
          .background(activeTint, CircleShape)
      )

      val coreScale = 1.0f + 0.18f * vadStrength * pulsePhase
      Box(
        modifier = Modifier
          .size(coreDotSize)
          .graphicsLayer {
            scaleX = coreScale
            scaleY = coreScale
          }
          .background(
            color = if (isVadActive) accentColor else inactiveColor.copy(alpha = 0.85f),
            shape = CircleShape
          )
      )
    }

    // Symmetrical multi-bar VAD-reactive waveform
    val barCount = if (compact) 10 else VadWaveformMath.DEFAULT_BAR_COUNT
    Canvas(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(vertical = if (compact) 2.dp else 3.dp)
    ) {
      val widthPx = size.width
      val heightPx = size.height
      if (widthPx <= 0f || heightPx <= 0f || barCount <= 0) return@Canvas

      val spacingRatio = 0.42f
      val slotWidth = widthPx / barCount.toFloat()
      val barWidth = (slotWidth * (1f - spacingRatio)).coerceIn(2.dp.toPx(), 6.dp.toPx())
      val gap = ((widthPx - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)).coerceAtLeast(1f)
      val minBarHeight = (heightPx * 0.16f).coerceAtLeast(3.dp.toPx())
      val centerY = heightPx * 0.5f
      val radius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)

      val activeBrush = Brush.verticalGradient(
        colors = listOf(
          activeTint.copy(alpha = 0.82f),
          activeTint,
          activeTint.copy(alpha = 0.82f)
        )
      )
      val idleColor = inactiveColor.copy(alpha = 0.40f)

      for (i in 0 until barCount) {
        val srcIdx = if (waveformBars.isNotEmpty()) {
          ((i * waveformBars.size) / barCount).coerceIn(0, waveformBars.lastIndex)
        } else {
          -1
        }
        val rawBar = if (srcIdx >= 0) waveformBars[srcIdx].coerceIn(0f, 1f) else 0f

        // Harmonic wave modulation when VAD is active so sustained Quranic madd vowels pulse smoothly
        val waveMod = if (vadStrength > 0.05f) {
          val waveAngle = (pulsePhase * PI * 2.0) + (i * 0.52)
          (0.84f + 0.16f * sin(waveAngle).toFloat())
        } else {
          1f
        }

        val combinedAmplitude = max(
          rawBar,
          smoothedMic * (0.55f + 0.45f * (1f - abs((i - barCount / 2f) / (barCount / 2f).coerceAtLeast(1f))))
        )
        val activeFraction = (combinedAmplitude * waveMod * (0.35f + 0.65f * vadStrength)).coerceIn(0f, 1f)
        val idleBreathing = 0.04f * pulsePhase * (1f - vadStrength)
        val barHeight = min(
          heightPx,
          minBarHeight + (heightPx - minBarHeight) * (activeFraction + idleBreathing).coerceIn(0f, 1f)
        )

        val x = i * (barWidth + gap)
        val top = centerY - barHeight * 0.5f

        if (vadStrength > 0.12f) {
          drawRoundRect(
            brush = activeBrush,
            topLeft = Offset(x, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
          )
        } else {
          drawRoundRect(
            color = idleColor,
            topLeft = Offset(x, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
          )
        }
      }
    }
  }
}
