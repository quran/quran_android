package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Audio-primary visual Tajweed scope replacing the Ustadh Noor figure when the user enables
 * **Audio Clues** mode on the existing **Sheikh Al-Minshawi Teacher** recitation audio.
 *
 * All timing, intensity, Madd holds, Ghunnah nasal resonance waves, Qalqalah echo pops, and
 * Tafkheem weight react live to the decoded Minshawi Teacher PCM waveform via
 * [AudioWaveformTajweedAnalyzer], using the Ayah's Tajweed text strictly as a soft label guide.
 */
@Composable
fun MinshawiAudioCluesCanvas(
  state: AudioTajweedClueSample,
  modifier: Modifier = Modifier
) {
  val frame = state.acousticFrame
  val hasActiveRule = state.activeRuleType.isArticulatedTajweedRule && state.isVoiceActive
  val showMaddBar = state.activeRuleType.maddCounts > 0 && state.isVoiceActive
  val showNasalWaves = state.activeRuleType.effect.isNasalSparkle && state.isVoiceActive
  val showQalqalahPop = state.activeRuleType == TajweedRuleType.QALQALAH && state.qalqalahPulse > 0.05f
  val showTafkheemAura = state.activeRuleType == TajweedRuleType.TAFKHEEM && state.isVoiceActive

  val accentColor by animateColorAsState(
    targetValue = when {
      state.mood == TeacherMood.CELEBRATING -> Color(0xFFFFB300)
      state.mood == TeacherMood.WORD_MATCHED -> Color(0xFF10B981)
      state.mood == TeacherMood.LISTENING -> Color(0xFF0EA5E9)
      hasActiveRule -> state.tajweedColor
      else -> Color(0xFF0F766E)
    },
    animationSpec = tween(180),
    label = "minshawiAccent"
  )

  val infinite = rememberInfiniteTransition(label = "minshawiPulse")
  val pulsePhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(760, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  val scopeDesc = if (hasActiveRule) {
    "Sheikh Al-Minshawi audio clue: ${state.cueLabelEn}"
  } else {
    "Sheikh Al-Minshawi live recitation waveform"
  }

  Column(
    modifier = modifier
      .semantics { contentDescription = scopeDesc }
      .testTag("minshawi_audio_clues_canvas"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (state.isSlowMo) 224.dp else 184.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(
          Brush.verticalGradient(
            colors = listOf(
              accentColor.copy(alpha = 0.07f),
              Color(0xFFF8FAFC)
            )
          )
        )
        .border(1.dp, accentColor.copy(alpha = 0.24f), RoundedCornerShape(20.dp)),
      contentAlignment = Alignment.Center
    ) {
      // Reserve horizontal margin on right when VerticalMaddBar is visible so waveform never collides
      val waveformEndPad = if (showMaddBar) 62.dp else 16.dp

      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = 16.dp, end = waveformEndPad, top = 28.dp, bottom = 28.dp)
      ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // 1. Ambient acoustic aura tinted by active Tajweed rule
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              accentColor.copy(alpha = (0.18f + 0.16f * frame.rms).coerceIn(0.06f, 0.36f)),
              accentColor.copy(alpha = 0.04f),
              Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxOf(size.width, size.height) * 0.56f
          ),
          radius = maxOf(size.width, size.height) * 0.56f,
          center = Offset(cx, cy)
        )

        // 2. Subtle acoustic center baseline
        drawLine(
          color = accentColor.copy(alpha = 0.22f),
          start = Offset(0f, cy),
          end = Offset(size.width, cy),
          strokeWidth = 2.5f,
          cap = StrokeCap.Round
        )

        // 3. Live 28-bar Sheikh Al-Minshawi symmetric waveform (100% live PCM driven)
        val bars = frame.waveformBars
        val count = bars.size.coerceAtLeast(1)
        val slotW = size.width / count.toFloat()
        val barW = (slotW * 0.56f).coerceIn(4f, 12f)
        val maxHalfH = size.height * 0.44f

        for (i in 0 until count) {
          val amp = bars[i].coerceIn(0f, 1f)
          val x = i * slotW + (slotW - barW) / 2f
          val halfH = (maxHalfH * (0.08f + 0.92f * amp)).coerceAtLeast(4f)
          val recencyWeight = 0.45f + 0.55f * (i.toFloat() / (count - 1).coerceAtLeast(1).toFloat())
          val barColor = if (hasActiveRule && i >= count - 10) {
            accentColor.copy(alpha = (0.65f + 0.35f * amp).coerceIn(0.4f, 1f))
          } else {
            accentColor.copy(alpha = (0.28f + 0.56f * recencyWeight).coerceIn(0.25f, 0.88f))
          }
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, cy - halfH),
            size = Size(barW, halfH * 2f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
          )
        }

        // 4. GREEN NASAL GHUNNAH / IKHFA / IQLAB RESONANCE ARCS (driven by <420 Hz audio hum)
        if (showNasalWaves) {
          val waveColor = state.tajweedColor
          for (ring in 0..2) {
            val frac = ((pulsePhase + ring * 0.33f) % 1f)
            val rx = size.width * (0.14f + 0.16f * frac)
            val ry = size.height * (0.18f + 0.16f * frac)
            drawArc(
              color = waveColor.copy(alpha = (1f - frac) * 0.82f),
              startAngle = 200f,
              sweepAngle = 140f,
              useCenter = false,
              topLeft = Offset(cx - rx, cy - ry - size.height * 0.14f),
              size = Size(rx * 2f, ry * 1.4f),
              style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
          }
        }

        // 5. BLUE QALQALAH ACOUSTIC DIP -> BURST ECHO RING
        if (showQalqalahPop) {
          val expand = 1f - state.qalqalahPulse
          val ringR = minOf(size.width, size.height) * (0.20f + 0.30f * expand)
          drawCircle(
            color = state.tajweedColor.copy(alpha = state.qalqalahPulse * 0.90f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = 6f)
          )
          drawCircle(
            color = state.tajweedColor.copy(alpha = state.qalqalahPulse * 0.45f),
            radius = ringR * 0.65f,
            center = Offset(cx, cy),
            style = Stroke(width = 3.5f)
          )
        }

        // 6. HEAVY TAFKHEEM LOW-MID WARM RESONANCE HALO
        if (showTafkheemAura) {
          val wobble = 1f + 0.06f * sin(pulsePhase * PI * 2.0).toFloat()
          drawOval(
            color = state.tajweedColor.copy(alpha = 0.24f * state.tafkheemIntensity),
            topLeft = Offset(cx - size.width * 0.34f * wobble, cy - size.height * 0.34f * wobble),
            size = Size(size.width * 0.68f * wobble, size.height * 0.68f * wobble),
            style = Stroke(width = 5f)
          )
        }
      }

      // Top-start audio source badge showing Sheikh Al-Minshawi Teacher acoustic tracking
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.90f),
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(top = 8.dp, start = 10.dp)
          .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(if (state.isVoiceActive) accentColor else Color(0xFF94A3B8))
          )
          Text(
            text = "🔊 صوت الشيخ المنشاوي",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
          )
        }
      }

      // Bottom-start compact 3-band live acoustic telemetry strip (Nasal / Vowel / Crisp)
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(bottom = 8.dp, start = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AcousticBandChip(
          label = "غنة",
          level = frame.lowBandRatio,
          color = TajweedRuleType.GHUNNAH_NOON.defaultColor
        )
        AcousticBandChip(
          label = "مد",
          level = frame.midBandRatio,
          color = TajweedRuleType.MADD_2.defaultColor
        )
        AcousticBandChip(
          label = "صدى",
          level = maxOf(frame.qalqalahBurstScore, frame.highBandRatio * 0.6f),
          color = TajweedRuleType.QALQALAH.defaultColor
        )
      }

      // Right-aligned VERTICAL MADD BAR driven directly by Sheikh Al-Minshawi's sustained vowel hold
      androidx.compose.animation.AnimatedVisibility(
        visible = showMaddBar,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(160)),
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 10.dp)
      ) {
        VerticalMaddBar(
          ruleType = state.activeRuleType,
          color = state.tajweedColor,
          progress = state.maddProgress,
          isSlowMo = state.isSlowMo
        )
      }

      // Stars pill overlay on celebration
      if (state.mood == TeacherMood.CELEBRATING && state.starsEarned > 0) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color(0xFFFFF8E1),
          shadowElevation = 3.dp,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 10.dp)
            .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(999.dp))
        ) {
          Text(
            text = "⭐".repeat(state.starsEarned.coerceIn(1, 3)),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
          )
        }
      }
    }

    // Active Audio-Driven Tajweed Clue Pill (only shown when waveform detects an active Tajweed rule)
    AnimatedVisibility(visible = hasActiveRule) {
      val pillColor = state.tajweedColor
      val labelText = buildString {
        append(state.cueLabelAr.ifBlank { state.activeRuleType.labelAr })
        if (state.matchedWordText.isNotBlank()) {
          append(" · ")
          append(state.matchedWordText)
        }
      }
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = pillColor.copy(alpha = 0.13f),
        modifier = Modifier
          .padding(top = 6.dp)
          .border(1.dp, pillColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
          .testTag("minshawi_audio_clue_pill")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(9.dp)
              .clip(CircleShape)
              .background(pillColor)
          )
          Text(
            text = labelText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = pillColor
          )
        }
      }
    }
  }
}

@Composable
private fun AcousticBandChip(
  label: String,
  level: Float,
  color: Color
) {
  val clamped = level.coerceIn(0f, 1f)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF475569)
    )
    Box(
      modifier = Modifier
        .width(26.dp)
        .height(5.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(color.copy(alpha = 0.18f))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(fraction = clamped.coerceIn(0.08f, 1f))
          .height(5.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(color.copy(alpha = 0.85f))
      )
    }
  }
}
