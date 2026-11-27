package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class SurahBadgeVisualStyle(
  val outerStart: Color,
  val outerEnd: Color,
  val rimHighlight: Color,
  val innerCoreStart: Color,
  val innerCoreEnd: Color,
  val cardAccent: Color,
  val badgeTextColor: Color
)

internal fun visualStyleForTier(tier: SurahBadgeTier): SurahBadgeVisualStyle = when (tier) {
  SurahBadgeTier.DIAMOND -> SurahBadgeVisualStyle(
    outerStart = Color(0xFF06B6D4),
    outerEnd = Color(0xFF0F766E),
    rimHighlight = Color(0xFFA5F3FC),
    innerCoreStart = Color(0xFFECFEFF),
    innerCoreEnd = Color(0xFF67E8F9),
    cardAccent = Color(0xFF06B6D4),
    badgeTextColor = Color(0xFF083344)
  )
  SurahBadgeTier.GOLD -> SurahBadgeVisualStyle(
    outerStart = Color(0xFFF59E0B),
    outerEnd = Color(0xFFB45309),
    rimHighlight = Color(0xFFFEF08A),
    innerCoreStart = Color(0xFFFFFBEB),
    innerCoreEnd = Color(0xFFFCD34D),
    cardAccent = Color(0xFFD97706),
    badgeTextColor = Color(0xFF451A03)
  )
  SurahBadgeTier.SILVER -> SurahBadgeVisualStyle(
    outerStart = Color(0xFF94A3B8),
    outerEnd = Color(0xFF475569),
    rimHighlight = Color(0xFFF1F5F9),
    innerCoreStart = Color(0xFFF8FAFC),
    innerCoreEnd = Color(0xFFCBD5E1),
    cardAccent = Color(0xFF64748B),
    badgeTextColor = Color(0xFF0F172A)
  )
  SurahBadgeTier.NONE -> SurahBadgeVisualStyle(
    outerStart = Color(0xFF64748B).copy(alpha = 0.35f),
    outerEnd = Color(0xFF334155).copy(alpha = 0.35f),
    rimHighlight = Color(0xFFCBD5E1).copy(alpha = 0.45f),
    innerCoreStart = Color(0xFFE2E8F0).copy(alpha = 0.35f),
    innerCoreEnd = Color(0xFF94A3B8).copy(alpha = 0.35f),
    cardAccent = Color(0xFF94A3B8),
    badgeTextColor = Color(0xFF475569)
  )
}

/**
 * Custom-drawn Islamic 8-point geometric rosette medallion representing a child's full-Surah
 * high-accuracy achievement crest (`Silver 80%+`, `Gold 90%+`, `Diamond 97%+`).
 */
@Composable
fun SurahBadgeMedallion(
  tier: SurahBadgeTier,
  centerLabel: String,
  modifier: Modifier = Modifier,
  size: Dp = 74.dp,
  animateGlow: Boolean = false
) {
  val style = remember(tier) { visualStyleForTier(tier) }
  val shimmerAlpha = if (animateGlow && tier != SurahBadgeTier.NONE) {
    val infinite = rememberInfiniteTransition(label = "badgeShimmer")
    val pulse by infinite.animateFloat(
      initialValue = 0.25f,
      targetValue = 0.72f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1900, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
      ),
      label = "glowPulse"
    )
    pulse
  } else {
    0.32f
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.size(size)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = this.size.width / 2f
      val cy = this.size.height / 2f
      val radius = minOf(cx, cy)

      // Subtle radiant halo behind earned crests
      if (tier != SurahBadgeTier.NONE) {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              style.rimHighlight.copy(alpha = shimmerAlpha),
              Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = radius
          ),
          radius = radius,
          center = Offset(cx, cy)
        )
      }

      // 8-point Rub el Hizb inspired scalloped star seal
      val outerR = radius * 0.90f
      val innerR = radius * 0.73f
      val points = 16
      val starPath = Path()
      for (i in 0 until points) {
        val angle = (i * (2.0 * PI / points)) - (PI / 2.0)
        val r = if (i % 2 == 0) outerR else innerR
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
      }
      starPath.close()

      drawPath(
        path = starPath,
        brush = Brush.linearGradient(
          colors = listOf(style.outerStart, style.outerEnd),
          start = Offset(0f, 0f),
          end = Offset(this.size.width, this.size.height)
        )
      )
      drawPath(
        path = starPath,
        color = style.rimHighlight.copy(alpha = 0.85f),
        style = Stroke(width = radius * 0.045f)
      )

      // Inner luminous circular core
      val coreR = radius * 0.60f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(style.innerCoreStart, style.innerCoreEnd),
          center = Offset(cx - coreR * 0.25f, cy - coreR * 0.25f),
          radius = coreR * 1.4f
        ),
        radius = coreR,
        center = Offset(cx, cy)
      )

      // Fine ornamental inner ring
      drawCircle(
        color = style.outerEnd.copy(alpha = 0.32f),
        radius = coreR * 0.86f,
        center = Offset(cx, cy),
        style = Stroke(width = radius * 0.025f)
      )

      // Top-left specular highlight arc for metallic sheen
      if (tier != SurahBadgeTier.NONE) {
        drawCircle(
          color = Color.White.copy(alpha = 0.48f),
          radius = radius * 0.11f,
          center = Offset(cx - coreR * 0.36f, cy - coreR * 0.38f)
        )
      }
    }

    Text(
      text = centerLabel,
      color = style.badgeTextColor,
      fontSize = if (size >= 70.dp) 18.sp else 14.sp,
      fontWeight = FontWeight.ExtraBold
    )
  }
}
