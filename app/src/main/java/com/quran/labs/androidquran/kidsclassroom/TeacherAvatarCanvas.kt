package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Six expressive moods for Ustadh Noor matching every phase of the Tilawi Kids Classroom.
 */
enum class TeacherMood {
  GREETING,
  RECITING,
  LISTENING,
  WORD_MATCHED,
  ENCOURAGING,
  CELEBRATING
}

/**
 * Snapshot rendered at 60 fps by [TeacherAvatarRenderer].
 */
data class TeacherAvatarState(
  val viseme: ArabicViseme = ArabicViseme.REST_CLOSED,
  val geometry: VisemeGeometry = TajweedVisemePlanner.defaultGeometryFor(viseme),
  val mouthOpenness: Float = geometry.jawOpen,
  val isVoiceActive: Boolean = false,
  val mood: TeacherMood = TeacherMood.GREETING,
  val activeRuleType: TajweedRuleType = TajweedRuleType.NORMAL,
  val tajweedColor: Color = TajweedRuleType.NORMAL.defaultColor,
  val harakahHint: String = "",
  val maddProgress: Float = 0f,
  val isQalqalahBounce: Boolean = false,
  val isSlowMo: Boolean = false,
  val starsEarned: Int = 0
)

/**
 * Swappable renderer abstraction so the built-in 60 fps Compose Canvas avatar
 * ([ComposeTeacherAvatar]) can later be swapped with a Rive `.riv` renderer without touching
 * classroom state or Tajweed lip-sync planning.
 */
interface TeacherAvatarRenderer {
  @Composable
  fun Render(
    state: TeacherAvatarState,
    modifier: Modifier
  )
}

/**
 * Built-in 60 fps Jetpack Compose [Canvas] implementation for **Ustadh Noor** — featuring
 * asymmetric spring-interpolated 18-viseme articulation, tiered natural jaw openings,
 * back-to-front clipped oral cavity + 6 tongue poses + upper/lower teeth + sculpted lips,
 * Tafkheem cheek fullness, and color-matched Tajweed visual cues.
 */
object ComposeTeacherAvatar : TeacherAvatarRenderer {

  @Composable
  override fun Render(
    state: TeacherAvatarState,
    modifier: Modifier
  ) {
    TeacherAvatarCanvas(state = state, modifier = modifier)
  }
}

@Composable
fun TeacherAvatarCanvas(
  state: TeacherAvatarState,
  modifier: Modifier = Modifier
) {
  // Enforce Tajweed-only mouth articulation: non-Tajweed (NORMAL / SILENT) letters keep mouth cleanly closed.
  val targetGeom = if (state.activeRuleType.isArticulatedTajweedRule) {
    state.geometry
  } else {
    TajweedVisemePlanner.defaultGeometryFor(ArabicViseme.REST_CLOSED)
  }

  // Asymmetric Spring Interpolation:
  // Fast attack (stiffness = 650f, damping = 0.85f) when entering lip/tongue consonant contact;
  // Smooth glide (stiffness = 280f, damping = 0.90f) when transitioning into Mudood or returning to rest.
  val activeSpring = if (state.activeRuleType.isArticulatedTajweedRule && state.viseme.usesFastConsonantAttack) {
    spring<Float>(dampingRatio = 0.85f, stiffness = 650f)
  } else {
    spring<Float>(dampingRatio = 0.90f, stiffness = 280f)
  }

  val jawOpen by animateFloatAsState(targetGeom.jawOpen, activeSpring, label = "jawOpen")
  val mouthWidth by animateFloatAsState(targetGeom.mouthWidth, activeSpring, label = "mouthWidth")
  val lipPucker by animateFloatAsState(targetGeom.lipPucker, activeSpring, label = "lipPucker")
  val lipPress by animateFloatAsState(targetGeom.lipPress, activeSpring, label = "lipPress")
  val upperTeethVis by animateFloatAsState(targetGeom.upperTeethVis, activeSpring, label = "upperTeeth")
  val lowerTeethVis by animateFloatAsState(targetGeom.lowerTeethVis, activeSpring, label = "lowerTeeth")
  val tongueLift by animateFloatAsState(targetGeom.tongueLift, activeSpring, label = "tongueLift")
  val cheekFullness by animateFloatAsState(targetGeom.cheekFullness, activeSpring, label = "cheekFull")

  // Smooth continuous weights for each anatomical TonguePose so tongue transitions never pop abruptly
  val tongueVisible by animateFloatAsState(
    if (targetGeom.tonguePose == TonguePose.HIDDEN) 0f else 1f,
    activeSpring,
    label = "tongueVis"
  )
  val tongueTipUp by animateFloatAsState(
    if (targetGeom.tonguePose == TonguePose.TIP_UP_ALVEOLAR) 1f else 0f,
    activeSpring,
    label = "tongueTipUp"
  )
  val tongueHoverMid by animateFloatAsState(
    if (targetGeom.tonguePose == TonguePose.HOVER_MID) 1f else 0f,
    activeSpring,
    label = "tongueHover"
  )
  val tongueArchMid by animateFloatAsState(
    if (targetGeom.tonguePose == TonguePose.ARCHED_MID_PALATE) 1f else 0f,
    activeSpring,
    label = "tongueArch"
  )
  val tongueBetweenTeeth by animateFloatAsState(
    if (targetGeom.tonguePose == TonguePose.BETWEEN_TEETH) 1f else 0f,
    activeSpring,
    label = "tongueBetween"
  )

  val nodAngle by animateFloatAsState(
    targetValue = when (state.mood) {
      TeacherMood.WORD_MATCHED -> 6.5f
      TeacherMood.LISTENING -> -3.5f
      TeacherMood.CELEBRATING -> 4.0f
      else -> 0f
    },
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
    label = "nodAngle"
  )

  val accentRingColor by animateColorAsState(
    targetValue = when {
      state.mood == TeacherMood.CELEBRATING -> Color(0xFFFFB300)
      state.mood == TeacherMood.WORD_MATCHED -> Color(0xFF10B981)
      state.mood == TeacherMood.LISTENING -> Color(0xFF0EA5E9)
      state.activeRuleType != TajweedRuleType.NORMAL && state.activeRuleType != TajweedRuleType.SILENT ->
        state.tajweedColor
      else -> Color(0xFF0F766E)
    },
    animationSpec = tween(220),
    label = "accentRing"
  )

  val infinite = rememberInfiniteTransition(label = "avatarAmbient")
  val breathPhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = (2f * PI).toFloat(),
    animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
    label = "breath"
  )
  val blinkWave by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
    label = "blink"
  )
  val pulsePhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(780, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  val eyeOpenFactor = if (blinkWave in 0.93f..0.98f) 0.1f else 1f
  val breathOffsetY = sin(breathPhase) * 2.2f

  val isActivelyVoicing = state.isVoiceActive || state.mouthOpenness > 0.03f
  val showMaddBar = state.activeRuleType.maddCounts > 0 && isActivelyVoicing
  // CRITICAL: GHUNNAH_MEEM & IQLAB have jawOpen == 0.0f (closed lips), so trigger sparkle on voice activity!
  val showGhunnahSparkle = state.activeRuleType.effect.isNasalSparkle && isActivelyVoicing
  val showQalqalahPop = state.activeRuleType == TajweedRuleType.QALQALAH &&
    (state.isQalqalahBounce || isActivelyVoicing)

  val avatarDesc = "Ustadh Noor teacher avatar, mood ${state.mood.name.lowercase()}"

  Column(
    modifier = modifier
      .semantics { contentDescription = avatarDesc }
      .testTag("teacher_avatar_canvas"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (state.isSlowMo) 236.dp else 188.dp),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.50f + breathOffsetY
        val faceR = minOf(size.width, size.height) * (if (state.isSlowMo) 0.39f else 0.36f)

        // 1. Warm sunlit background aura + Tajweed color halo
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              accentRingColor.copy(alpha = 0.24f + 0.08f * pulsePhase),
              accentRingColor.copy(alpha = 0.06f),
              Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = faceR * 1.52f
          ),
          radius = faceR * 1.52f,
          center = Offset(cx, cy)
        )

        // Listening soundwave rings around Ustadh Noor when child is reciting
        if (state.mood == TeacherMood.LISTENING) {
          for (i in 0..2) {
            val ringFrac = ((pulsePhase + i * 0.33f) % 1f)
            drawCircle(
              color = Color(0xFF0EA5E9).copy(alpha = (1f - ringFrac) * 0.38f),
              radius = faceR * (1.06f + ringFrac * 0.42f),
              center = Offset(cx, cy),
              style = Stroke(width = 3.5f)
            )
          }
        }

        // Celebration confetti & star burst
        if (state.mood == TeacherMood.CELEBRATING || state.mood == TeacherMood.WORD_MATCHED) {
          drawCelebrationSparkles(
            cx = cx,
            cy = cy,
            radius = faceR * 1.24f,
            pulse = pulsePhase,
            isFullCelebration = state.mood == TeacherMood.CELEBRATING
          )
        }

        // Rotate head gently for nod / listening tilt
        rotate(degrees = nodAngle, pivot = Offset(cx, cy + faceR * 0.3f)) {
          // Shoulders / clean emerald thobe collar
          drawOval(
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFF0F766E), Color(0xFF0B534D)),
              startY = cy + faceR * 0.74f,
              endY = cy + faceR * 1.38f
            ),
            topLeft = Offset(cx - faceR * 0.92f, cy + faceR * 0.74f),
            size = Size(faceR * 1.84f, faceR * 0.68f)
          )
          // Neck
          drawRoundRect(
            color = Color(0xFFE7B88E),
            topLeft = Offset(cx - faceR * 0.22f, cy + faceR * 0.64f),
            size = Size(faceR * 0.44f, faceR * 0.32f),
            cornerRadius = CornerRadius(faceR * 0.14f)
          )

          // Ears
          drawCircle(Color(0xFFE8BC94), radius = faceR * 0.20f, center = Offset(cx - faceR * 0.88f, cy + faceR * 0.02f))
          drawCircle(Color(0xFFD69F73), radius = faceR * 0.11f, center = Offset(cx - faceR * 0.86f, cy + faceR * 0.02f))
          drawCircle(Color(0xFFE8BC94), radius = faceR * 0.20f, center = Offset(cx + faceR * 0.88f, cy + faceR * 0.02f))
          drawCircle(Color(0xFFD69F73), radius = faceR * 0.11f, center = Offset(cx + faceR * 0.86f, cy + faceR * 0.02f))

          // Listening cue beside ear when LISTENING
          if (state.mood == TeacherMood.LISTENING) {
            drawArc(
              color = Color(0xFF0EA5E9),
              startAngle = -55f,
              sweepAngle = 110f,
              useCenter = false,
              topLeft = Offset(cx + faceR * 0.88f, cy - faceR * 0.26f),
              size = Size(faceR * 0.36f, faceR * 0.52f),
              style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )
            drawArc(
              color = Color(0xFF38BDF8).copy(alpha = 0.65f + 0.3f * pulsePhase),
              startAngle = -50f,
              sweepAngle = 100f,
              useCenter = false,
              topLeft = Offset(cx + faceR * 1.00f, cy - faceR * 0.36f),
              size = Size(faceR * 0.46f, faceR * 0.72f),
              style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
          }

          // Main friendly rounded face (with subtle lower-jaw drop tracking wide Mudood naturally)
          val jawChinDrop = faceR * 0.08f * jawOpen
          drawOval(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFFFDE0C4), Color(0xFFF3C69F)),
              center = Offset(cx, cy - faceR * 0.15f),
              radius = faceR * 1.12f
            ),
            topLeft = Offset(cx - faceR * 0.88f, cy - faceR * 0.92f),
            size = Size(faceR * 1.76f, faceR * 1.84f + jawChinDrop)
          )

          // Heavy Tafkheem cheek fullness cue (خ ص ض غ ط ق ظ)
          if (cheekFullness > 0.04f) {
            val puffAlpha = (0.16f + 0.22f * cheekFullness).coerceIn(0f, 0.42f)
            val puffR = faceR * (0.24f + 0.08f * cheekFullness)
            for (side in listOf(-1f, 1f)) {
              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(
                    Color(0xFFFFECD6).copy(alpha = puffAlpha * 1.3f),
                    Color(0xFFE0A375).copy(alpha = puffAlpha),
                    Color.Transparent
                  ),
                  center = Offset(cx + side * faceR * 0.52f, cy + faceR * 0.24f),
                  radius = puffR
                ),
                radius = puffR,
                center = Offset(cx + side * faceR * 0.52f, cy + faceR * 0.24f)
              )
            }
          }

          // Warm friendly trimmed beard contour framing the chin cleanly below the articulated mouth
          val beardPath = Path().apply {
            moveTo(cx - faceR * 0.82f, cy + faceR * 0.16f)
            quadraticTo(cx - faceR * 0.78f, cy + faceR * 0.95f + jawChinDrop, cx, cy + faceR * 0.97f + jawChinDrop)
            quadraticTo(cx + faceR * 0.78f, cy + faceR * 0.95f + jawChinDrop, cx + faceR * 0.82f, cy + faceR * 0.16f)
            quadraticTo(cx + faceR * 0.66f, cy + faceR * 0.76f + jawChinDrop, cx, cy + faceR * 0.78f + jawChinDrop)
            quadraticTo(cx - faceR * 0.66f, cy + faceR * 0.76f + jawChinDrop, cx - faceR * 0.82f, cy + faceR * 0.16f)
            close()
          }
          drawPath(beardPath, color = Color(0xFF4A3426))

          // Warm embroidered cream & gold Kufi cap
          val capPath = Path().apply {
            moveTo(cx - faceR * 0.86f, cy - faceR * 0.36f)
            quadraticTo(cx - faceR * 0.88f, cy - faceR * 1.08f, cx, cy - faceR * 1.06f)
            quadraticTo(cx + faceR * 0.88f, cy - faceR * 1.08f, cx + faceR * 0.86f, cy - faceR * 0.36f)
            quadraticTo(cx, cy - faceR * 0.56f, cx - faceR * 0.86f, cy - faceR * 0.36f)
            close()
          }
          drawPath(
            path = capPath,
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFFFFFDF7), Color(0xFFEDE6D6)),
              startY = cy - faceR * 1.08f,
              endY = cy - faceR * 0.34f
            )
          )
          val trimPath = Path().apply {
            moveTo(cx - faceR * 0.83f, cy - faceR * 0.44f)
            quadraticTo(cx, cy - faceR * 0.62f, cx + faceR * 0.83f, cy - faceR * 0.44f)
          }
          drawPath(
            path = trimPath,
            color = Color(0xFFD4AF37),
            style = Stroke(width = faceR * 0.065f, cap = StrokeCap.Round)
          )

          // Soft rosy cheeks
          drawOval(
            color = Color(0xFFF48FB1).copy(alpha = 0.25f),
            topLeft = Offset(cx - faceR * 0.68f, cy + faceR * 0.04f),
            size = Size(faceR * 0.30f, faceR * 0.17f)
          )
          drawOval(
            color = Color(0xFFF48FB1).copy(alpha = 0.25f),
            topLeft = Offset(cx + faceR * 0.38f, cy + faceR * 0.04f),
            size = Size(faceR * 0.30f, faceR * 0.17f)
          )

          // Expressive eyebrows
          val browLift = when (state.mood) {
            TeacherMood.CELEBRATING, TeacherMood.WORD_MATCHED -> -faceR * 0.07f
            TeacherMood.ENCOURAGING -> -faceR * 0.04f
            TeacherMood.RECITING -> -faceR * 0.035f * jawOpen
            else -> 0f
          }
          drawEyebrows(cx, cy, faceR, browLift)

          // Expressive eyes
          val happyEyes = state.mood == TeacherMood.CELEBRATING || state.mood == TeacherMood.WORD_MATCHED
          drawEyes(cx, cy, faceR, eyeOpenFactor, happyEyes)

          // Friendly nose
          val noseY = cy + faceR * 0.06f
          drawArc(
            color = Color(0xFFC88F63),
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(cx - faceR * 0.12f, noseY - faceR * 0.06f),
            size = Size(faceR * 0.24f, faceR * 0.18f),
            style = Stroke(width = faceR * 0.045f, cap = StrokeCap.Round)
          )

          // GREEN GHUNNAH / IKHFA / IQLAB NASAL SHIMMER right beside the nose!
          if (showGhunnahSparkle) {
            drawGhunnahNasalSparkle(
              cx = cx,
              noseY = noseY,
              faceR = faceR,
              pulse = pulsePhase,
              color = state.tajweedColor
            )
          }

          // PRECISION 18-VISEME ARTICULATED MOUTH (strict back-to-front Z-order!)
          val mouthCenterY = cy + faceR * 0.42f + jawChinDrop * 0.35f
          drawPrecisionArticulatedMouth(
            cx = cx,
            cy = mouthCenterY,
            faceR = faceR,
            jawOpen = jawOpen,
            mouthWidth = mouthWidth,
            lipPucker = lipPucker,
            lipPress = lipPress,
            upperTeethVis = upperTeethVis,
            lowerTeethVis = lowerTeethVis,
            tongueVisible = tongueVisible,
            tongueTipUp = tongueTipUp,
            tongueHoverMid = tongueHoverMid,
            tongueArchMid = tongueArchMid,
            tongueBetweenTeeth = tongueBetweenTeeth,
            tongueLift = tongueLift,
            isLabiodentalFa = state.viseme == ArabicViseme.TEETH_FA,
            isHappyMood = state.mood == TeacherMood.GREETING ||
              state.mood == TeacherMood.CELEBRATING ||
              state.mood == TeacherMood.WORD_MATCHED,
            isSlowMo = state.isSlowMo
          )

          // BLUE QALQALAH BOUNCE RING popping from the lips!
          if (showQalqalahPop) {
            drawQalqalahBounceRing(
              cx = cx,
              mouthY = mouthCenterY,
              faceR = faceR,
              pulse = pulsePhase,
              color = state.tajweedColor
            )
          }
        }
      }

      // Vertical color-matched MADD bar beside Ustadh Noor (2 / 4 / 6 counts climbing vertically!)
      androidx.compose.animation.AnimatedVisibility(
        visible = showMaddBar,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(180)),
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
            .align(Alignment.TopStart)
            .padding(top = 6.dp, start = 8.dp)
            .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(999.dp))
        ) {
          Text(
            text = "⭐".repeat(state.starsEarned.coerceIn(1, 3)),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
          )
        }
      }
    }

    // Active Tajweed rule cue pill below Ustadh Noor (only for articulated Tajweed rules, including closed-lip Ghunnah Meem & Iqlab)
    AnimatedVisibility(
      visible = state.activeRuleType.isArticulatedTajweedRule && isActivelyVoicing
    ) {
      val pillColor = state.tajweedColor
      val labelText = state.harakahHint.ifBlank {
        "${state.activeRuleType.labelAr} · ${state.activeRuleType.labelEn}"
      }
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = pillColor.copy(alpha = 0.13f),
        modifier = Modifier
          .padding(top = 4.dp)
          .border(1.dp, pillColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
          .testTag("tajweed_cue_pill")
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
internal fun VerticalMaddBar(
  ruleType: TajweedRuleType,
  color: Color,
  progress: Float,
  isSlowMo: Boolean
) {
  val totalCounts = ruleType.maddCounts.coerceIn(2, 6)
  val clampedProgress = progress.coerceIn(0f, 1f)
  val animatedFill by animateFloatAsState(
    targetValue = clampedProgress.coerceIn(0.10f, 1f),
    animationSpec = tween(durationMillis = 80, easing = LinearEasing),
    label = "maddVerticalFill"
  )
  val filledCounts = (clampedProgress * totalCounts + 0.5f).toInt().coerceIn(1, totalCounts)
  val barHeight = if (isSlowMo) 180.dp else 148.dp

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.11f),
    shadowElevation = 2.dp,
    modifier = Modifier
      .width(42.dp)
      .height(barHeight)
      .border(1.4.dp, color.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
      .semantics { contentDescription = "${ruleType.labelEn}, $filledCounts of $totalCounts counts" }
      .testTag("madd_vertical_bar")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(vertical = 8.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Text(
        text = "$filledCounts/$totalCounts",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color
      )

      Canvas(
        modifier = Modifier
          .width(14.dp)
          .weight(1f)
      ) {
        val corner = CornerRadius(size.width / 2f, size.width / 2f)

        // Track background
        drawRoundRect(
          color = color.copy(alpha = 0.18f),
          topLeft = Offset.Zero,
          size = size,
          cornerRadius = corner
        )

        // Rising vertical fill from bottom upward
        val fillH = (size.height * animatedFill).coerceIn(size.width, size.height)
        val fillTop = size.height - fillH
        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              color.copy(alpha = 0.82f),
              color
            ),
            startY = fillTop,
            endY = size.height
          ),
          topLeft = Offset(0f, fillTop),
          size = Size(size.width, fillH),
          cornerRadius = corner
        )

        // Subtle luminous cap highlight at the rising tip
        drawCircle(
          color = Color.White.copy(alpha = 0.65f),
          radius = size.width * 0.24f,
          center = Offset(size.width / 2f, fillTop + size.width * 0.42f)
        )

        // Crisp horizontal count tick dividers for 2 / 4 / 6 Madd counts
        for (tick in 1 until totalCounts) {
          val tickY = size.height * (1f - tick.toFloat() / totalCounts.toFloat())
          drawLine(
            color = Color.White.copy(alpha = 0.78f),
            start = Offset(2f, tickY),
            end = Offset(size.width - 2f, tickY),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
          )
        }
      }

      Text(
        text = "مد",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color
      )
    }
  }
}

private fun DrawScope.drawEyebrows(cx: Float, cy: Float, faceR: Float, lift: Float) {
  val browColor = Color(0xFF3E2723)
  val browStroke = Stroke(width = faceR * 0.075f, cap = StrokeCap.Round)
  val y = cy - faceR * 0.30f + lift
  drawPath(
    path = Path().apply {
      moveTo(cx - faceR * 0.54f, y + faceR * 0.03f)
      quadraticTo(cx - faceR * 0.34f, y - faceR * 0.08f, cx - faceR * 0.16f, y + faceR * 0.02f)
    },
    color = browColor,
    style = browStroke
  )
  drawPath(
    path = Path().apply {
      moveTo(cx + faceR * 0.16f, y + faceR * 0.02f)
      quadraticTo(cx + faceR * 0.34f, y - faceR * 0.08f, cx + faceR * 0.54f, y + faceR * 0.03f)
    },
    color = browColor,
    style = browStroke
  )
}

private fun DrawScope.drawEyes(
  cx: Float,
  cy: Float,
  faceR: Float,
  openFactor: Float,
  happyCrescents: Boolean
) {
  val eyeY = cy - faceR * 0.10f
  val eyeDx = faceR * 0.34f
  val eyeRx = faceR * 0.145f
  val eyeRy = faceR * 0.16f * openFactor.coerceIn(0.08f, 1f)

  if (happyCrescents) {
    val arcStroke = Stroke(width = faceR * 0.075f, cap = StrokeCap.Round)
    for (dir in listOf(-1f, 1f)) {
      val ex = cx + dir * eyeDx
      drawArc(
        color = Color(0xFF2B1D16),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(ex - eyeRx, eyeY - eyeRx * 0.65f),
        size = Size(eyeRx * 2f, eyeRx * 1.4f),
        style = arcStroke
      )
    }
    return
  }

  for (dir in listOf(-1f, 1f)) {
    val ex = cx + dir * eyeDx
    drawOval(
      color = Color.White,
      topLeft = Offset(ex - eyeRx, eyeY - eyeRy),
      size = Size(eyeRx * 2f, eyeRy * 2f)
    )
    val pupilR = minOf(eyeRx, eyeRy) * 0.68f
    drawCircle(
      color = Color(0xFF2B1B12),
      radius = pupilR,
      center = Offset(ex, eyeY)
    )
    if (openFactor > 0.4f) {
      drawCircle(
        color = Color.White,
        radius = pupilR * 0.34f,
        center = Offset(ex - pupilR * 0.32f, eyeY - pupilR * 0.30f)
      )
    }
  }
}

/**
 * Rule 2.3.5: Draws Ustadh Noor's mouth interior in strict back-to-front Z-order:
 * 1. Deep inner mouth cavity (dark maroon gradient, clipped to inner lip contour).
 * 2. Articulated Pink Tongue (`FLAT_RELAXED`, `TIP_UP_ALVEOLAR`, `HOVER_MID`, `ARCHED_MID_PALATE`).
 * 3. Upper & lower white rounded teeth rows (plus `BETWEEN_TEETH` protruding tongue tip drawn
 *    *over* lower teeth and tucked beneath upper teeth, and `TEETH_FA` upper teeth resting on lower lip).
 * 4. Smooth outer lips (Cupid's bow upper lip + fuller lower lip shaped by `mouthWidth`, `jawOpen`,
 *    `lipPucker`, and `lipPress`).
 */
private fun DrawScope.drawPrecisionArticulatedMouth(
  cx: Float,
  cy: Float,
  faceR: Float,
  jawOpen: Float,
  mouthWidth: Float,
  lipPucker: Float,
  lipPress: Float,
  upperTeethVis: Float,
  lowerTeethVis: Float,
  tongueVisible: Float,
  tongueTipUp: Float,
  tongueHoverMid: Float,
  tongueArchMid: Float,
  tongueBetweenTeeth: Float,
  tongueLift: Float,
  isLabiodentalFa: Boolean,
  isHappyMood: Boolean,
  isSlowMo: Boolean
) {
  val scaleBoost = if (isSlowMo) 1.18f else 1.0f
  val clampedWidth = mouthWidth.coerceIn(0.32f, 1.0f)
  val clampedJaw = jawOpen.coerceIn(0.0f, 0.88f)
  val clampedPucker = lipPucker.coerceIn(0.0f, 1.0f)
  val clampedPress = lipPress.coerceIn(0.0f, 1.0f)

  val halfW = faceR * 0.46f * clampedWidth * scaleBoost
  // Tiered natural vertical opening: 0.0f closed -> ~0.12*faceR middle -> ~0.27*faceR full Alif Madd
  val halfH = faceR * 0.33f * clampedJaw * scaleBoost

  // --- CLOSED OR BILABIAL-PRESSED LIPS (REST_CLOSED, LIPS_MEEM_HOLD م/مّ/Iqlab, LIPS_BA_POP ب Phase 1) ---
  if (clampedJaw < 0.035f || clampedPress > 0.52f) {
    // Firm press (ب = 1.0f) flattens and compresses lips; REST_CLOSED curves into a calm warm smile
    val smileLift = when {
      clampedPress > 0.80f -> 0.0f
      clampedPress > 0.50f -> faceR * 0.025f
      isHappyMood -> faceR * 0.065f
      else -> faceR * 0.04f
    }
    val upperLipThickness = faceR * (if (clampedPress > 0.80f) 0.045f else 0.055f) * scaleBoost
    val lowerLipThickness = faceR * (if (clampedPress > 0.80f) 0.052f else 0.068f) * scaleBoost

    // Upper closed lip with gentle Cupid's bow
    val upperClosedPath = Path().apply {
      moveTo(cx - halfW, cy - smileLift * 0.5f)
      quadraticTo(cx - halfW * 0.35f, cy - upperLipThickness + smileLift * 0.25f, cx, cy - upperLipThickness * 0.72f + smileLift * 0.4f)
      quadraticTo(cx + halfW * 0.35f, cy - upperLipThickness + smileLift * 0.25f, cx + halfW, cy - smileLift * 0.5f)
      quadraticTo(cx, cy + smileLift, cx - halfW, cy - smileLift * 0.5f)
      close()
    }
    // Lower cushioned closed lip
    val lowerClosedPath = Path().apply {
      moveTo(cx - halfW * 0.96f, cy - smileLift * 0.45f)
      quadraticTo(cx, cy + smileLift, cx + halfW * 0.96f, cy - smileLift * 0.45f)
      quadraticTo(cx, cy + smileLift + lowerLipThickness * 1.45f, cx - halfW * 0.96f, cy - smileLift * 0.45f)
      close()
    }
    val lipFill = if (clampedPress > 0.82f) Color(0xFFB5484E) else Color(0xFFC65B60)
    drawPath(upperClosedPath, color = lipFill)
    drawPath(lowerClosedPath, color = lipFill.copy(red = (lipFill.red * 1.04f).coerceAtMost(1f)))

    // Soft highlight on lower lip (shows wet bilabial compression on ب / م)
    drawLine(
      color = Color.White.copy(alpha = if (clampedPress > 0.80f) 0.32f else 0.22f),
      start = Offset(cx - halfW * 0.32f, cy + smileLift * 0.6f + lowerLipThickness * 0.45f),
      end = Offset(cx + halfW * 0.32f, cy + smileLift * 0.6f + lowerLipThickness * 0.45f),
      strokeWidth = faceR * 0.022f,
      cap = StrokeCap.Round
    )

    // Crisp seam line where upper & lower lips meet
    val seamPath = Path().apply {
      moveTo(cx - halfW, cy - smileLift * 0.5f)
      quadraticTo(cx, cy + smileLift, cx + halfW, cy - smileLift * 0.5f)
    }
    drawPath(
      path = seamPath,
      color = Color(0xFF692026),
      style = Stroke(width = faceR * (if (clampedPress > 0.80f) 0.036f else 0.028f), cap = StrokeCap.Round)
    )
    return
  }

  // --- OPEN / ARTICULATED MOUTH CONTOUR ---
  // Smile corner lift for wide Kasra / Ya vs rounded circular contour for Damma / Waw pucker
  val cornerSmileLift = when {
    clampedPucker > 0.45f -> -faceR * 0.02f * clampedPucker
    clampedWidth > 0.78f -> faceR * 0.055f * ((clampedWidth - 0.65f) / 0.30f).coerceIn(0f, 1f)
    else -> faceR * 0.015f
  }

  val topY = cy - halfH
  val bottomY = cy + halfH
  val cornerY = cy - cornerSmileLift

  // Build smooth inner lip contour Path (oval/circular when puckered, anatomically curved when smiling/open)
  val innerLipPath = Path().apply {
    if (clampedPucker > 0.55f) {
      // Forward circular "O" aperture for Waw / Damma
      addOval(
        androidx.compose.ui.geometry.Rect(
          left = cx - halfW,
          top = topY,
          right = cx + halfW,
          bottom = bottomY
        )
      )
    } else {
      moveTo(cx - halfW, cornerY)
      cubicTo(
        cx - halfW * 0.55f, topY - faceR * 0.015f,
        cx + halfW * 0.55f, topY - faceR * 0.015f,
        cx + halfW, cornerY
      )
      cubicTo(
        cx + halfW * 0.62f, bottomY + faceR * 0.025f,
        cx - halfW * 0.62f, bottomY + faceR * 0.025f,
        cx - halfW, cornerY
      )
      close()
    }
  }

  val cavityW = halfW * 2f
  val cavityH = (bottomY - topY).coerceAtLeast(faceR * 0.06f)

  // Z-LAYER 1, 2, 3 (clipped strictly inside innerLipPath)
  clipPath(innerLipPath) {
    // 1. Deep inner mouth cavity (dark maroon gradient)
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF3E0D15), Color(0xFF6B1825), Color(0xFF52111B)),
        startY = topY,
        endY = bottomY
      ),
      topLeft = Offset(cx - halfW - faceR * 0.05f, topY - faceR * 0.05f),
      size = Size(cavityW + faceR * 0.10f, cavityH + faceR * 0.10f)
    )

    // 2. Articulated Pink Tongue (for internal poses: FLAT_RELAXED, TIP_UP_ALVEOLAR, HOVER_MID, ARCHED_MID_PALATE)
    if (tongueVisible > 0.06f && tongueBetweenTeeth < 0.75f) {
      val internalAlpha = (tongueVisible * (1f - tongueBetweenTeeth * 0.6f)).coerceIn(0f, 1f)
      drawInternalTonguePose(
        cx = cx,
        topY = topY,
        bottomY = bottomY,
        cavityW = cavityW,
        cavityH = cavityH,
        faceR = faceR,
        tipUp = tongueTipUp,
        hoverMid = tongueHoverMid,
        archMid = tongueArchMid,
        lift = tongueLift,
        alpha = internalAlpha
      )
    }

    // 3a. Lower white rounded teeth row (drawn BEFORE interdental tongue tip so ث/ذ/ظ tongue rests OVER lower teeth!)
    if (lowerTeethVis > 0.06f && !isLabiodentalFa) {
      val ltW = cavityW * 0.74f
      val ltH = (cavityH * 0.25f * lowerTeethVis).coerceIn(faceR * 0.032f, faceR * 0.085f)
      val ltTop = bottomY - ltH + faceR * 0.008f
      drawTeethRow(
        cx = cx,
        top = ltTop,
        width = ltW,
        height = ltH,
        faceR = faceR,
        alpha = lowerTeethVis.coerceIn(0f, 1f)
      )
    }

    // 2b. BETWEEN_TEETH protruding pink tongue tip (ث / ذ / ظ) drawn OVER lower teeth and UNDER upper teeth!
    if (tongueBetweenTeeth > 0.08f) {
      val tipW = cavityW * (0.52f + 0.10f * tongueBetweenTeeth)
      val tipH = (cavityH * 0.62f).coerceAtLeast(faceR * 0.11f)
      val tipTop = cy - tipH * 0.28f
      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFFF26D85).copy(alpha = tongueBetweenTeeth),
            Color(0xFFFF8DA1).copy(alpha = tongueBetweenTeeth)
          ),
          startY = tipTop,
          endY = tipTop + tipH
        ),
        topLeft = Offset(cx - tipW / 2f, tipTop),
        size = Size(tipW, tipH),
        cornerRadius = CornerRadius(tipW * 0.46f, tipW * 0.46f)
      )
      // Subtle center tongue groove
      drawLine(
        color = Color(0xFFD14764).copy(alpha = 0.65f * tongueBetweenTeeth),
        start = Offset(cx, tipTop + tipH * 0.12f),
        end = Offset(cx, tipTop + tipH * 0.72f),
        strokeWidth = faceR * 0.018f,
        cap = StrokeCap.Round
      )
    }

    // 3b. Upper white rounded teeth row (rests cleanly above TIP_UP_ALVEOLAR & BETWEEN_TEETH!)
    if (upperTeethVis > 0.06f && !isLabiodentalFa) {
      val utW = cavityW * 0.80f
      val utH = (cavityH * 0.28f * upperTeethVis).coerceIn(faceR * 0.038f, faceR * 0.098f)
      val utTop = topY - faceR * 0.008f
      drawTeethRow(
        cx = cx,
        top = utTop,
        width = utW,
        height = utH,
        faceR = faceR,
        alpha = upperTeethVis.coerceIn(0f, 1f)
      )
    }
  }

  // Z-LAYER 4: Smooth outer sculpted lips (Cupid's bow upper lip + fuller lower lip + pucker shading)
  val lipRimWidth = faceR * (0.056f + 0.028f * clampedPucker) * scaleBoost
  val lipBaseColor = if (clampedPucker > 0.5f) Color(0xFFBC4E54) else Color(0xFFC85A5F)

  drawPath(
    path = innerLipPath,
    color = lipBaseColor,
    style = Stroke(width = lipRimWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
  )

  // Subtle Cupid's bow peak on upper lip when not tightly puckered
  if (clampedPucker < 0.55f) {
    val bowPath = Path().apply {
      moveTo(cx - halfW * 0.42f, topY - lipRimWidth * 0.18f)
      quadraticTo(cx - halfW * 0.15f, topY - lipRimWidth * 0.52f, cx, topY - lipRimWidth * 0.22f)
      quadraticTo(cx + halfW * 0.15f, topY - lipRimWidth * 0.52f, cx + halfW * 0.42f, topY - lipRimWidth * 0.18f)
    }
    drawPath(
      path = bowPath,
      color = lipBaseColor,
      style = Stroke(width = lipRimWidth * 0.55f, cap = StrokeCap.Round)
    )
  } else {
    // Circular pucker highlight ring for Waw (و) & Damma (ـُ)
    drawOval(
      color = Color(0xFF8C2D35).copy(alpha = 0.45f * clampedPucker),
      topLeft = Offset(cx - halfW - lipRimWidth * 0.45f, topY - lipRimWidth * 0.45f),
      size = Size(cavityW + lipRimWidth * 0.9f, cavityH + lipRimWidth * 0.9f),
      style = Stroke(width = faceR * 0.018f)
    )
  }

  // Labiodental Fa (ف): Upper front white teeth visibly resting ON TOP OF the gently tucked lower lip!
  if (isLabiodentalFa || (upperTeethVis > 0.85f && lowerTeethVis < 0.05f && clampedPress > 0.20f)) {
    val faTeethW = cavityW * 0.68f
    val faTeethH = (cavityH * 0.68f).coerceIn(faceR * 0.055f, faceR * 0.10f)
    drawTeethRow(
      cx = cx,
      top = topY + lipRimWidth * 0.10f,
      width = faTeethW,
      height = faTeethH,
      faceR = faceR,
      alpha = 0.98f
    )
  }

  // Soft glossy highlight along the fuller lower lip
  drawArc(
    color = Color.White.copy(alpha = 0.24f),
    startAngle = 25f,
    sweepAngle = 130f,
    useCenter = false,
    topLeft = Offset(cx - halfW * 0.55f, bottomY - lipRimWidth * 0.35f),
    size = Size(halfW * 1.10f, lipRimWidth * 0.85f),
    style = Stroke(width = faceR * 0.018f, cap = StrokeCap.Round)
  )
}

private fun DrawScope.drawInternalTonguePose(
  cx: Float,
  topY: Float,
  bottomY: Float,
  cavityW: Float,
  cavityH: Float,
  faceR: Float,
  tipUp: Float,
  hoverMid: Float,
  archMid: Float,
  lift: Float,
  alpha: Float
) {
  val tongueW = cavityW * (0.66f - 0.10f * tipUp)
  val baseBottom = bottomY + faceR * 0.03f

  // Compute where the apex of the tongue sits inside the oral cavity:
  // - FLAT_RELAXED: low gentle cushion near bottom (apex at bottomY - 0.34*cavityH)
  // - HOVER_MID (Ikhfa): clearly suspended mid-cavity without touching upper teeth (topY + 0.38*cavityH)
  // - ARCHED_MID_PALATE (ج/ش/ي/ض): broad mid-palate dome (topY + 0.28*cavityH)
  // - TIP_UP_ALVEOLAR (ن/ل/ر/ت/د/ط): narrow rounded pink tip lifted right up to upper gum ridge (topY + 0.08*cavityH)
  val relaxedApexY = bottomY - cavityH * 0.34f
  val hoverApexY = topY + cavityH * 0.40f
  val archApexY = topY + cavityH * 0.28f
  val alveolarApexY = topY + cavityH * (0.18f - 0.12f * lift.coerceIn(0f, 1f))

  val otherWeight = (1f - tipUp - hoverMid - archMid).coerceAtLeast(0f)
  val denom = (otherWeight + tipUp + hoverMid + archMid).coerceAtLeast(0.001f)
  val apexY = (
    relaxedApexY * otherWeight +
      alveolarApexY * tipUp +
      hoverApexY * hoverMid +
      archApexY * archMid
    ) / denom

  if (hoverMid > 0.45f) {
    // HOVER_MID (Green Ikhfa): Draw tongue clearly floating mid-mouth with dark space above AND below!
    val hoverH = (cavityH * 0.38f).coerceAtLeast(faceR * 0.06f)
    val hoverTop = (topY + cavityH * 0.33f).coerceAtMost(bottomY - hoverH * 0.8f)
    drawRoundRect(
      brush = Brush.verticalGradient(
        colors = listOf(
          Color(0xFFFF859D).copy(alpha = alpha),
          Color(0xFFE35B76).copy(alpha = alpha)
        ),
        startY = hoverTop,
        endY = hoverTop + hoverH
      ),
      topLeft = Offset(cx - tongueW * 0.48f, hoverTop),
      size = Size(tongueW * 0.96f, hoverH),
      cornerRadius = CornerRadius(hoverH * 0.5f, hoverH * 0.5f)
    )
    return
  }

  // Smooth curved tongue body rising from floor of mouth to apexY
  val tipHalfWidth = tongueW * (0.46f - 0.18f * tipUp + 0.06f * archMid)
  val tonguePath = Path().apply {
    moveTo(cx - tongueW * 0.52f, baseBottom)
    cubicTo(
      cx - tongueW * 0.50f, apexY + cavityH * 0.18f,
      cx - tipHalfWidth, apexY,
      cx, apexY
    )
    cubicTo(
      cx + tipHalfWidth, apexY,
      cx + tongueW * 0.50f, apexY + cavityH * 0.18f,
      cx + tongueW * 0.52f, baseBottom
    )
    close()
  }

  drawPath(
    path = tonguePath,
    brush = Brush.verticalGradient(
      colors = listOf(
        Color(0xFFFF889E).copy(alpha = alpha),
        Color(0xFFE35A75).copy(alpha = alpha)
      ),
      startY = apexY,
      endY = baseBottom
    )
  )

  // Highlight on raised alveolar tongue tip (ن / ل / ر / ت / د / ط) so kids unmistakably see the lifted tip!
  if (tipUp > 0.30f) {
    val tipBadgeR = (tipHalfWidth * 0.62f).coerceAtLeast(faceR * 0.035f)
    drawOval(
      color = Color(0xFFFFA6B8).copy(alpha = 0.78f * tipUp * alpha),
      topLeft = Offset(cx - tipBadgeR, apexY + faceR * 0.01f),
      size = Size(tipBadgeR * 2f, tipBadgeR * 1.15f)
    )
  } else {
    // Subtle median tongue line on relaxed/arched tongue
    drawLine(
      color = Color(0xFFC94460).copy(alpha = 0.45f * alpha),
      start = Offset(cx, apexY + cavityH * 0.10f),
      end = Offset(cx, bottomY - cavityH * 0.05f),
      strokeWidth = faceR * 0.016f,
      cap = StrokeCap.Round
    )
  }
}

private fun DrawScope.drawTeethRow(
  cx: Float,
  top: Float,
  width: Float,
  height: Float,
  faceR: Float,
  alpha: Float
) {
  val left = cx - width / 2f
  drawRoundRect(
    color = Color(0xFFFFFEFA).copy(alpha = alpha),
    topLeft = Offset(left, top),
    size = Size(width, height),
    cornerRadius = CornerRadius(faceR * 0.032f, faceR * 0.032f)
  )
  // Subtle tooth dividers so teeth look crisp and friendly rather than a flat white block
  val dividerColor = Color(0xFFDCE3EA).copy(alpha = 0.75f * alpha)
  for (frac in listOf(0.25f, 0.50f, 0.75f)) {
    val tx = left + width * frac
    drawLine(
      color = dividerColor,
      start = Offset(tx, top + height * 0.15f),
      end = Offset(tx, top + height * 0.85f),
      strokeWidth = faceR * 0.010f
    )
  }
}

private fun DrawScope.drawGhunnahNasalSparkle(
  cx: Float,
  noseY: Float,
  faceR: Float,
  pulse: Float,
  color: Color
) {
  val vibrOffset = sin(pulse * PI * 2.0).toFloat() * faceR * 0.03f
  for (side in listOf(-1f, 1f)) {
    val sx = cx + side * faceR * 0.28f + vibrOffset * side
    val sy = noseY - faceR * 0.02f
    drawCircle(
      color = color.copy(alpha = 0.26f + 0.28f * pulse),
      radius = faceR * (0.11f + 0.06f * pulse),
      center = Offset(sx, sy)
    )
    val arm = faceR * (0.09f + 0.04f * pulse)
    val strokeW = faceR * 0.028f
    drawLine(color, Offset(sx - arm, sy), Offset(sx + arm, sy), strokeWidth = strokeW, cap = StrokeCap.Round)
    drawLine(color, Offset(sx, sy - arm), Offset(sx, sy + arm), strokeWidth = strokeW, cap = StrokeCap.Round)
  }
}

private fun DrawScope.drawQalqalahBounceRing(
  cx: Float,
  mouthY: Float,
  faceR: Float,
  pulse: Float,
  color: Color
) {
  val ringR = faceR * (0.36f + 0.28f * pulse)
  drawCircle(
    color = color.copy(alpha = (1f - pulse * 0.65f) * 0.85f),
    radius = ringR,
    center = Offset(cx, mouthY),
    style = Stroke(width = faceR * 0.055f)
  )
}

private fun DrawScope.drawCelebrationSparkles(
  cx: Float,
  cy: Float,
  radius: Float,
  pulse: Float,
  isFullCelebration: Boolean
) {
  val palette = if (isFullCelebration) {
    listOf(Color(0xFFFFB300), Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFFF97316))
  } else {
    listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFFFFB300))
  }
  val count = if (isFullCelebration) 10 else 6
  for (i in 0 until count) {
    val angle = (2.0 * PI * i / count) + pulse * 0.35
    val dist = radius * (0.92f + 0.16f * sin(pulse * PI + i).toFloat())
    val px = cx + cos(angle).toFloat() * dist
    val py = cy + sin(angle).toFloat() * dist
    val col = palette[i % palette.size]
    val r = if (isFullCelebration) 7.5f + 3f * pulse else 6f + 2f * pulse
    drawCircle(color = col.copy(alpha = 0.85f), radius = r, center = Offset(px, py))
  }
}
